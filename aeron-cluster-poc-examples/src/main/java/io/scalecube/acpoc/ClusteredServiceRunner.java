package io.scalecube.acpoc;

import io.aeron.agent.EventLogAgent;
import io.aeron.archive.Archive;
import io.aeron.archive.client.AeronArchive;
import io.aeron.cluster.ClusterControl;
import io.aeron.cluster.ClusteredMediaDriver;
import io.aeron.cluster.ConsensusModule;
import io.aeron.cluster.ConsensusModule.Configuration;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.cluster.service.ClusteredServiceContainer;
import io.aeron.driver.DefaultAllowTerminationValidator;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.MinMulticastFlowControlSupplier;
import java.io.File;
import java.nio.file.Paths;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.agrona.CloseHelper;
import org.agrona.concurrent.status.AtomicCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Main class that starts single node in cluster, though expecting most of cluster configuration
 * passed via VM args.
 */
public class ClusteredServiceRunner {

  private static final Logger logger = LoggerFactory.getLogger(ClusteredServiceRunner.class);

  /**
   * Main function runner.
   *
   * @param args arguments
   */
  public static void main(String[] args) {
    System.setProperty("aeron.event.cluster.log", "all");
    System.setProperty("aeron.event.archive.log", "all");
    System.setProperty("aeron.event.log", "admin");
    EventLogAgent.agentmain("", ByteBuddyAgent.install());

    String clusterMemberId = Integer.toHexString(Configuration.clusterMemberId());
    String nodeId = "node-" + clusterMemberId + "-" + Utils.instanceId();
    String nodeDirName = Paths.get("target", "aeron", "cluster", nodeId).toString();

    System.out.println("Cluster node directory: " + nodeDirName);

    MediaDriver.Context mediaDriverContext =
        new MediaDriver.Context()
            .errorHandler(ex -> logger.error("Exception occurred at MediaDriver: ", ex))
            .terminationHook(() -> logger.info("TerminationHook called on MediaDriver "))
            .terminationValidator(new DefaultAllowTerminationValidator())
            .warnIfDirectoryExists(true)
            .dirDeleteOnStart(true)
            .dirDeleteOnShutdown(true)
            .multicastFlowControlSupplier(new MinMulticastFlowControlSupplier());

    AeronArchive.Context aeronArchiveContext =
        new AeronArchive.Context()
            .aeronDirectoryName(mediaDriverContext.aeronDirectoryName())
            .controlRequestChannel("aeron:ipc?term-length=64k|alias=controlRequest")
            .controlResponseChannel("aeron:ipc?term-length=64k|alias=controlResponse")
            .errorHandler(ex -> logger.error("[AeronArchive] exception:", ex));

    Archive.Context archiveContext =
        new Archive.Context()
            .aeronDirectoryName(mediaDriverContext.aeronDirectoryName())
            .archiveDir(new File(nodeDirName, "archive"))
            .recordingEventsEnabled(false)
            // .controlChannel(aeronArchiveContext.controlRequestChannel())
            .controlStreamId(aeronArchiveContext.controlRequestStreamId())
            .localControlChannel(aeronArchiveContext.controlRequestChannel())
            .localControlStreamId(aeronArchiveContext.controlRequestStreamId())
            .errorHandler(ex -> logger.error("Exception occurred at Archive: ", ex));

    ConsensusModule.Context consensusModuleContext =
        new ConsensusModule.Context()
            .errorHandler(ex -> logger.error("Exception occurred at ConsensusModule: ", ex))
            .terminationHook(() -> logger.info("TerminationHook called on ConsensusModule"))
            .aeronDirectoryName(mediaDriverContext.aeronDirectoryName())
            .clusterDir(new File(nodeDirName, "consensus"))
            .archiveContext(aeronArchiveContext.clone())
            .replicationChannel("aeron:udp?endpoint=localhost:0");

    ClusteredMediaDriver clusteredMediaDriver =
        ClusteredMediaDriver.launch(mediaDriverContext, archiveContext, consensusModuleContext);

    AtomicCounter controlToggle =
        ClusterControl.findControlToggle(
            mediaDriverContext.countersManager(),
            clusteredMediaDriver.consensusModule().context().clusterId());

    ClusteredService clusteredService = new ClusteredServiceImpl(controlToggle);

    ClusteredServiceContainer.Context clusteredServiceCtx =
        new ClusteredServiceContainer.Context()
            .errorHandler(ex -> logger.error("Exception occurred: " + ex, ex))
            .aeronDirectoryName(clusteredMediaDriver.mediaDriver().aeronDirectoryName())
            .archiveContext(aeronArchiveContext.clone())
            .clusterDir(new File(nodeDirName, "service"))
            .clusteredService(clusteredService);

    ClusteredServiceContainer clusteredServiceContainer =
        ClusteredServiceContainer.launch(clusteredServiceCtx);

    Mono<Void> onShutdown =
        Utils.onShutdown(
            () -> {
              CloseHelper.close(clusteredServiceContainer);
              CloseHelper.close(clusteredMediaDriver);
              return null;
            });
    onShutdown.block();
  }
}
