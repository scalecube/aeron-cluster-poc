package io.aeron.cluster;

import io.aeron.Aeron;
import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.archive.client.AeronArchive;
import io.aeron.cluster.ConsensusModule.Configuration;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.cluster.service.ClusteredServiceContainer;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.MediaDriver.Context;
import io.aeron.driver.MinMulticastFlowControlSupplier;
import io.aeron.driver.ThreadingMode;
import io.scalecube.acpoc.ClusteredServiceImpl;
import io.scalecube.acpoc.Configurations;
import io.scalecube.acpoc.Utils;
import java.io.File;
import java.nio.file.Paths;
import org.agrona.CloseHelper;
import org.agrona.IoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class ConsensusModuleAgentExample {

  private static final Logger logger = LoggerFactory.getLogger(ConsensusModuleAgentExample.class);

  public static void main(String[] args) {
    //    ConsensusModule.Context context =
    //        new ConsensusModule.Context().idleStrategySupplier(YieldingIdleStrategy::new);
    //
    //    ConsensusModuleAgent agent1 = new ConsensusModuleAgent(context.clone());
    //    ConsensusModuleAgent agent2 = new ConsensusModuleAgent(context.clone());
    //    ConsensusModuleAgent agent3 = new ConsensusModuleAgent(context.clone());
    //
    //    DynamicCompositeAgent compositeAgent =
    //        new DynamicCompositeAgent("dynamicCompositeAgent", agent1, agent2, agent3);
    //
    //    AgentRunner.startOnThread(
    //        new AgentRunner(
    //            context.idleStrategy(),
    //            context.errorHandler(),
    //            context.errorCounter(),
    //            compositeAgent));

    String clusterMemberId = Integer.toHexString(Configuration.clusterMemberId());
    String nodeId = "node-" + clusterMemberId + "-" + Utils.instanceId();
    String nodeDirName = Paths.get(IoUtil.tmpDirName(), "aeron", "cluster", nodeId).toString();

    if (Configurations.CLEAN_START) {
      IoUtil.delete(new File(nodeDirName), true);
    }

    System.out.println("Cluster node directory: " + nodeDirName);

    String aeronDirectoryName = Paths.get(nodeDirName, "media").toString();

    MediaDriver.Context mediaDriverContext = mediaDriverContext(aeronDirectoryName);

    final MediaDriver mediaDriver =
        MediaDriver.launch(mediaDriverContext.spiesSimulateConnection(true));

    AeronArchive.Context aeronArchiveContext =
        new AeronArchive.Context().aeronDirectoryName(aeronDirectoryName);

    Archive.Context archiveContext =
        new Archive.Context()
            .maxCatalogEntries(Configurations.MAX_CATALOG_ENTRIES)
            .aeronDirectoryName(aeronDirectoryName)
            .archiveDir(new File(nodeDirName, "archive"))
            .controlChannel(aeronArchiveContext.controlRequestChannel())
            .controlStreamId(aeronArchiveContext.controlRequestStreamId())
            .localControlStreamId(aeronArchiveContext.controlRequestStreamId())
            .recordingEventsChannel(aeronArchiveContext.recordingEventsChannel())
            .threadingMode(ArchiveThreadingMode.SHARED);

    ConsensusModule.Context consensusModuleCtx =
        new ConsensusModule.Context()
            .errorHandler(ex -> logger.error("Exception occurred: " + ex, ex))
            .aeronDirectoryName(aeronDirectoryName)
            .clusterDir(new File(nodeDirName, "consensus-module"))
            .archiveContext(aeronArchiveContext.clone());

    ClusteredMediaDriver clusteredMediaDriver =
        ClusteredMediaDriver.launch(mediaDriverContext, archiveContext, consensusModuleCtx);

    ClusteredService clusteredService =
        new ClusteredServiceImpl(clusteredMediaDriver.mediaDriver().context().countersManager());

    ClusteredServiceContainer.Context clusteredServiceCtx =
        new ClusteredServiceContainer.Context()
            .errorHandler(ex -> logger.error("Exception occurred: " + ex, ex))
            .aeronDirectoryName(aeronDirectoryName)
            .archiveContext(aeronArchiveContext.clone())
            .clusterDir(new File(nodeDirName, "service"))
            .clusteredService(clusteredService);

    ClusteredServiceContainer clusteredServiceContainer =
        ClusteredServiceContainer.launch(clusteredServiceCtx);

    Mono<Void> onShutdown =
        Utils.onShutdown(
            () -> {
              CloseHelper.close(clusteredMediaDriver);
              CloseHelper.close(clusteredServiceContainer);
              if (Configurations.CLEAN_SHUTDOWN) {
                IoUtil.delete(new File(nodeDirName), true);
              }
              return null;
            });
    onShutdown.block();
  }

  private static Context mediaDriverContext(String aeronDirectoryName) {
    return new Context()
        .errorHandler(ex -> logger.error("Exception occurred: " + ex, ex))
        .aeronDirectoryName(aeronDirectoryName)
        .threadingMode(ThreadingMode.SHARED)
        .multicastFlowControlSupplier(new MinMulticastFlowControlSupplier());
  }
}
