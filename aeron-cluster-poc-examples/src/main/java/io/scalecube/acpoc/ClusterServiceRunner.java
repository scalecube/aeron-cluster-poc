package io.scalecube.acpoc;

import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.archive.client.AeronArchive;
import io.aeron.cluster.ConsensusModule;
import io.aeron.cluster.ConsensusModule.Configuration;
import io.aeron.cluster.ExtendedConsensusModuleAgent;
import io.aeron.cluster.service.ClusteredServiceContainer;
import io.aeron.cluster.service.ExtendedClusteredServiceAgent;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.MediaDriver.Context;
import io.aeron.driver.MinMulticastFlowControlSupplier;
import io.aeron.driver.ThreadingMode;
import io.aeron.driver.status.SystemCounterDescriptor;
import java.io.File;
import java.nio.file.Paths;
import org.agrona.IoUtil;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.DynamicCompositeAgent;
import org.agrona.concurrent.status.CountersManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Main class that starts single node in cluster, though expecting most of cluster configuration
 * passed via VM args.
 */
public class ClusterServiceRunner {

  private static final Logger logger = LoggerFactory.getLogger(ClusterServiceRunner.class);

  /**
   * Main function runner.
   *
   * @param args arguments
   */
  public static void main(String[] args) {
    String clusterMemberId = Integer.toHexString(Configuration.clusterMemberId());
    String nodeId = "node-" + clusterMemberId + "-" + Utils.instanceId();
    String nodeDirName = Paths.get(IoUtil.tmpDirName(), "aeron", "cluster", nodeId).toString();

    if (Configurations.CLEAN_START) {
      IoUtil.delete(new File(nodeDirName), true);
    }

    System.out.println("Cluster node directory: " + nodeDirName);

    String aeronDirectoryName = Paths.get(nodeDirName, "media").toString();

    MediaDriver.Context mediaDriverContext = mediaDriverContext(aeronDirectoryName);
    MediaDriver mediaDriver = MediaDriver.launch(mediaDriverContext.spiesSimulateConnection(true));

    AeronArchive.Context aeronArchiveContext =
        new AeronArchive.Context().aeronDirectoryName(aeronDirectoryName);

    ConsensusModule.Context consensusModuleContext1 =
        consensusModuleContext(1, nodeDirName, aeronDirectoryName, aeronArchiveContext);
    ExtendedConsensusModuleAgent consensusModuleAgent1 =
        new ExtendedConsensusModuleAgent(consensusModuleContext1);

    Archive.Context archiveContext1 =
        archiveContext(1, nodeDirName, aeronDirectoryName, aeronArchiveContext);

    Agent archiveAgent1 =
        Archive.launch(
                archiveContext1
                    .threadingMode(ArchiveThreadingMode.INVOKER)
                    .errorHandler( //
                        mediaDriverContext.errorHandler())
                    .errorCounter(
                        mediaDriverContext.systemCounters().get(SystemCounterDescriptor.ERRORS)))
            .invoker()
            .agent();

    ClusteredServiceContainer.Context clusteredServiceContext1 =
        clusteredServiceContext(
            1,
            nodeDirName,
            aeronDirectoryName,
            aeronArchiveContext,
            mediaDriverContext.countersManager());

    ExtendedClusteredServiceAgent serviceAgent1 =
        new ExtendedClusteredServiceAgent(clusteredServiceContext1);

    AgentRunner.startOnThread(
        new AgentRunner(
            archiveContext1.idleStrategy(),
            archiveContext1.errorHandler(),
            archiveContext1.errorCounter(),
            new DynamicCompositeAgent("compositeArchiveAgent", archiveAgent1)));

    AgentRunner.startOnThread(
        new AgentRunner(
            consensusModuleContext1.idleStrategy(),
            consensusModuleContext1.errorHandler(),
            consensusModuleContext1.errorCounter(),
            new DynamicCompositeAgent("compositeConsensusModuleAgent", consensusModuleAgent1)));

    AgentRunner.startOnThread(
        new AgentRunner(
            clusteredServiceContext1.idleStrategy(),
            clusteredServiceContext1.errorHandler(),
            clusteredServiceContext1.errorCounter(),
            new DynamicCompositeAgent("compositeServiceAgent", serviceAgent1)));

    Mono<Void> onShutdown =
        Utils.onShutdown(
            () -> {
              if (Configurations.CLEAN_SHUTDOWN) {
                IoUtil.delete(new File(nodeDirName), true);
              }
              return null;
            });
    onShutdown.block();
  }

  private static ClusteredServiceContainer.Context clusteredServiceContext(
      int instance,
      String nodeDirName,
      String aeronDirectoryName,
      AeronArchive.Context aeronArchiveContext,
      CountersManager countersManager) {
    return new ClusteredServiceContainer.Context()
        .errorHandler(ex -> logger.error("Exception occurred: " + ex, ex))
        .aeronDirectoryName(aeronDirectoryName)
        .archiveContext(aeronArchiveContext.clone())
        .clusterDir(new File(nodeDirName, "service-" + instance))
        .clusteredService(new ClusteredServiceImpl(countersManager));
  }

  private static ConsensusModule.Context consensusModuleContext(
      int instance,
      String nodeDirName,
      String aeronDirectoryName,
      AeronArchive.Context aeronArchiveContext) {
    return new ConsensusModule.Context()
        .errorHandler(ex -> logger.error("Exception occurred: " + ex, ex))
        .aeronDirectoryName(aeronDirectoryName)
        .clusterDir(new File(nodeDirName, "consensus-module-" + instance))
        .archiveContext(aeronArchiveContext.clone());
  }

  private static Archive.Context archiveContext(
      int instance,
      String nodeDirName,
      String aeronDirectoryName,
      AeronArchive.Context aeronArchiveContext) {
    return new Archive.Context()
        .maxCatalogEntries(Configurations.MAX_CATALOG_ENTRIES)
        .aeronDirectoryName(aeronDirectoryName)
        .archiveDir(new File(nodeDirName, "archive-" + instance))
        .controlChannel(aeronArchiveContext.controlRequestChannel())
        .controlStreamId(aeronArchiveContext.controlRequestStreamId())
        .localControlStreamId(aeronArchiveContext.controlRequestStreamId())
        .recordingEventsChannel(aeronArchiveContext.recordingEventsChannel())
        .threadingMode(ArchiveThreadingMode.SHARED);
  }

  private static Context mediaDriverContext(String aeronDirectoryName) {
    return new Context()
        .errorHandler(ex -> logger.error("Exception occurred: " + ex, ex))
        .aeronDirectoryName(aeronDirectoryName)
        .threadingMode(ThreadingMode.SHARED)
        .multicastFlowControlSupplier(new MinMulticastFlowControlSupplier());
  }
}
