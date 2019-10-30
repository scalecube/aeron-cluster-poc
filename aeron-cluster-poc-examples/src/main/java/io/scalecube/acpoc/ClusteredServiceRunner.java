package io.scalecube.acpoc;

import io.aeron.agent.EventConfiguration;
import io.aeron.agent.EventLogAgent;
import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.archive.client.AeronArchive;
import io.aeron.cluster.ClusteredMediaDriver;
import io.aeron.cluster.ConsensusModule;
import io.aeron.cluster.ConsensusModule.Configuration;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.cluster.service.ClusteredServiceContainer;
import io.aeron.driver.DefaultAllowTerminationValidator;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.MinMulticastFlowControlSupplier;
import io.aeron.driver.ThreadingMode;
import java.io.File;
import java.nio.file.Paths;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.agrona.CloseHelper;
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
    System.setProperty(
        EventConfiguration.ENABLED_EVENT_CODES_PROP_NAME,
        "CMD_IN_ADD_PUBLICATION,"
            + "CMD_IN_REMOVE_PUBLICATION,"
            + "CMD_IN_ADD_SUBSCRIPTION,"
            + "CMD_IN_REMOVE_SUBSCRIPTION,"
            + "CMD_OUT_PUBLICATION_READY,"
            + "CMD_OUT_AVAILABLE_IMAGE,"
            + "INVOCATION,"
            + "CMD_OUT_ON_OPERATION_SUCCESS,"
            + "CMD_IN_KEEPALIVE_CLIENT,"
            + "REMOVE_PUBLICATION_CLEANUP,"
            + "REMOVE_SUBSCRIPTION_CLEANUP,"
            + "REMOVE_IMAGE_CLEANUP,"
            + "CMD_OUT_ON_UNAVAILABLE_IMAGE,"
            + "SEND_CHANNEL_CREATION,"
            + "RECEIVE_CHANNEL_CREATION,"
            + "SEND_CHANNEL_CLOSE,"
            + "RECEIVE_CHANNEL_CLOSE,"
            + "CMD_IN_REMOVE_DESTINATION,"
            + "CMD_IN_ADD_EXCLUSIVE_PUBLICATION,"
            + "CMD_OUT_EXCLUSIVE_PUBLICATION_READY,"
            + "CMD_OUT_ERROR,"
            + "CMD_IN_ADD_COUNTER,"
            + "CMD_IN_REMOVE_COUNTER,"
            + "CMD_OUT_SUBSCRIPTION_READY,"
            + "CMD_OUT_COUNTER_READY,"
            + "CMD_OUT_ON_UNAVAILABLE_COUNTER,"
            + "CMD_IN_CLIENT_CLOSE,"
            + "CMD_IN_ADD_RCV_DESTINATION,"
            + "CMD_IN_REMOVE_RCV_DESTINATION,"
            + "CMD_OUT_ON_CLIENT_TIMEOUT,"
            + "CMD_IN_TERMINATE_DRIVER");
    System.setProperty(EventConfiguration.ENABLED_CLUSTER_EVENT_CODES_PROP_NAME, "all");
    EventLogAgent.agentmain("", ByteBuddyAgent.install());

    String clusterMemberId = Integer.toHexString(Configuration.clusterMemberId());
    String nodeId = "node-" + clusterMemberId + "-" + Utils.instanceId();
    String nodeDirName = Paths.get("target", "aeron", "cluster", nodeId).toString();

    System.out.println("Cluster node directory: " + nodeDirName);

    String aeronDirectoryName = Paths.get(nodeDirName, "media").toString();

    AeronArchive.Context aeronArchiveContext =
        new AeronArchive.Context().aeronDirectoryName(aeronDirectoryName);

    MediaDriver.Context mediaDriverContext =
        new MediaDriver.Context()
            // .errorHandler(ex -> logger.error("Exception occurred at MediaDriver: ", ex))
            .terminationHook(() -> logger.info("TerminationHook called on MediaDriver "))
            .terminationValidator(new DefaultAllowTerminationValidator())
            .threadingMode(ThreadingMode.SHARED)
            .warnIfDirectoryExists(true)
            .dirDeleteOnStart(true)
            .aeronDirectoryName(aeronDirectoryName)
            .multicastFlowControlSupplier(new MinMulticastFlowControlSupplier());

    Archive.Context archiveContext =
        new Archive.Context()
            // .errorHandler(ex -> logger.error("Exception occurred at Archive: ", ex))
            .maxCatalogEntries(Configurations.MAX_CATALOG_ENTRIES)
            .aeronDirectoryName(aeronDirectoryName)
            .archiveDir(new File(nodeDirName, "archive"))
            .controlChannel(aeronArchiveContext.controlRequestChannel())
            .controlStreamId(aeronArchiveContext.controlRequestStreamId())
            .localControlStreamId(aeronArchiveContext.controlRequestStreamId())
            .recordingEventsChannel(aeronArchiveContext.recordingEventsChannel())
            .threadingMode(ArchiveThreadingMode.SHARED);

    ConsensusModule.Context consensusModuleContext =
        new ConsensusModule.Context()
            // .errorHandler(ex -> logger.error("Exception occurred at ConsensusModule: ", ex))
            .terminationHook(() -> logger.info("TerminationHook called on ConsensusModule"))
            .aeronDirectoryName(aeronDirectoryName)
            .clusterDir(new File(nodeDirName, "consensus"))
            .archiveContext(aeronArchiveContext.clone());

    ClusteredMediaDriver clusteredMediaDriver =
        ClusteredMediaDriver.launch(mediaDriverContext, archiveContext, consensusModuleContext);

    ClusteredService clusteredService =
        new ClusteredServiceImpl(clusteredMediaDriver.mediaDriver().context().countersManager());

    ClusteredServiceContainer.Context clusteredServiceCtx =
        new ClusteredServiceContainer.Context()
            // .errorHandler(ex -> logger.error("Exception occurred: " + ex, ex))
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
              return null;
            });
    onShutdown.block();
  }
}
