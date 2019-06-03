package io.aeron.cluster;

import io.aeron.Aeron;
import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.archive.client.AeronArchive;
import io.aeron.cluster.service.ClusteredServiceContainer;
import io.aeron.cluster.service.ExtendedClusteredServiceAgent;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.MinMulticastFlowControlSupplier;
import io.aeron.driver.ThreadingMode;
import io.aeron.driver.status.SystemCounterDescriptor;
import io.scalecube.acpoc.ClusteredServiceImpl;
import io.scalecube.acpoc.Configurations;
import java.io.File;
import java.nio.file.Paths;
import org.agrona.IoUtil;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.DynamicCompositeAgent;
import org.agrona.concurrent.YieldingIdleStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main class that starts single node in cluster, though expecting most of cluster configuration
 * passed via VM args.
 */
public class ClusterServiceSharedAeronRunner {

  private static final Logger logger =
      LoggerFactory.getLogger(ClusterServiceSharedAeronRunner.class);

  /**
   * Main function runner.
   *
   * @param args arguments
   */
  public static void main(String[] args) throws InterruptedException {
    //    String clusterMemberId = Integer.toHexString(Configuration.clusterMemberId());
    //    String nodeId = "node-" + clusterMemberId + "-" + Utils.instanceId();
    String nodeDirName = Paths.get(IoUtil.tmpDirName(), "aeron", "cluster", "shared").toString();

    if (Configurations.CLEAN_START) {
      IoUtil.delete(new File(nodeDirName), true);
    }

    System.out.println("Cluster node directory: " + nodeDirName);

    String aeronDirectoryName = Paths.get(nodeDirName, "media").toString();

    MediaDriver.Context mediaDriverCtx =
        new MediaDriver.Context()
            .errorHandler(ex -> logger.error("Exception occurred: " + ex, ex))
            .aeronDirectoryName(aeronDirectoryName)
            .threadingMode(ThreadingMode.SHARED)
            .multicastFlowControlSupplier(new MinMulticastFlowControlSupplier())
            .spiesSimulateConnection(true);

    MediaDriver mediaDriver = MediaDriver.launch(mediaDriverCtx);

    Aeron aeron =
        Aeron.connect(
            new Aeron.Context()
                .useConductorAgentInvoker(true)
                .aeronDirectoryName(aeronDirectoryName));

    int size = 3;

    AeronArchive.Context[] aeronArchiveCtxs = new AeronArchive.Context[size];
    for (int i = 0; i < size; i++) {
      aeronArchiveCtxs[i] =
          new AeronArchive.Context()
              .aeron(aeron)
              .ownsAeronClient(false)
              .controlRequestChannel("aeron:udp?endpoint=localhost:801" + i)
              .controlResponseChannel("aeron:udp?endpoint=localhost:802" + i)
              .recordingEventsChannel("aeron:udp?control-mode=dynamic|control=localhost:803" + i);
    }

    Archive.Context[] archiveCtxs = new Archive.Context[size];
    for (int i = 0; i < size; i++) {
      archiveCtxs[i] =
          new Archive.Context()
              .maxCatalogEntries(Configurations.MAX_CATALOG_ENTRIES)
              .aeron(aeron)
              .ownsAeronClient(false)
              .archiveDir(new File(nodeDirName, "archive-" + i))
              .controlChannel(aeronArchiveCtxs[i].controlRequestChannel())
              .controlStreamId(aeronArchiveCtxs[i].controlRequestStreamId())
              .localControlStreamId(aeronArchiveCtxs[i].controlRequestStreamId())
              .recordingEventsChannel(aeronArchiveCtxs[i].recordingEventsChannel())
              .threadingMode(ArchiveThreadingMode.INVOKER);
    }

    Agent[] archiveAgents = new Agent[size];
    for (int i = 0; i < size; i++) {
      Archive archive =
          Archive.launch(
              archiveCtxs[i]
                  .aeron(aeron)
                  .ownsAeronClient(false)
                  .errorHandler(mediaDriverCtx.errorHandler())
                  .errorCounter(
                      mediaDriverCtx.systemCounters().get(SystemCounterDescriptor.ERRORS)));
      archiveAgents[i] = archive.invoker().agent();
    }

    Agent[] consensusModuleAgents = new Agent[size];
    for (int i = 0; i < size; i++) {
      consensusModuleAgents[i] =
          new ConsensusModuleAgent(
              new ConsensusModule.Context()
                  .errorHandler(ex -> logger.error("Exception occurred: " + ex, ex))
                  .aeron(aeron)
                  .ownsAeronClient(false)
                  .clusterDir(new File(nodeDirName, "consensus-module-" + i))
                  .idleStrategySupplier(() -> new YieldingIdleStrategy())
                  // .clusterNodeCounter(// todo)
                  .archiveContext(aeronArchiveCtxs[i].clone()));
    }

    Agent[] clusteredServiceAgents = new Agent[size];
    for (int i = 0; i < size; i++) {
      clusteredServiceAgents[i] =
          new ExtendedClusteredServiceAgent(
              new ClusteredServiceContainer.Context()
                  .errorHandler(ex -> logger.error("Exception occurred: " + ex, ex))
                  .aeron(aeron)
                  .ownsAeronClient(false)
                  .archiveContext(aeronArchiveCtxs[i].clone())
                  .clusterDir(new File(nodeDirName, "service-" + i))
                  .clusteredService(new ClusteredServiceImpl(mediaDriverCtx.countersManager())));
    }

    Agent[] agents = new Agent[size * 3];

    for (int i = 0; i < size; i++) {
      agents[i * size] = archiveAgents[i];
      agents[(i + 1) * size] = consensusModuleAgents[i];
      agents[(i + 2) * size] = clusteredServiceAgents[i];
    }

    DynamicCompositeAgent compositeAgent = new DynamicCompositeAgent("composite", agents);

    AgentRunner.startOnThread(
        new AgentRunner(
            new YieldingIdleStrategy(), Throwable::printStackTrace, null, compositeAgent));

    Thread.currentThread().join();
  }
}
