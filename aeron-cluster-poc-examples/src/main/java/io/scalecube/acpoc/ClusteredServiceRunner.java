package io.scalecube.acpoc;

import io.aeron.archive.Archive;
import io.aeron.archive.client.AeronArchive;
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
    //    System.setProperty(EventConfiguration.ENABLED_CLUSTER_EVENT_CODES_PROP_NAME, "all");
    //    System.setProperty(EventConfiguration.ENABLED_ARCHIVE_EVENT_CODES_PROP_NAME, "all");
    //    System.setProperty(EventConfiguration.ENABLED_EVENT_CODES_PROP_NAME, "all");
    //    System.setProperty(EventConfiguration.ENABLED_EVENT_CODES_PROP_NAME, "admin");
    //    EventLogAgent.agentmain("", ByteBuddyAgent.install());

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
        new AeronArchive.Context().aeronDirectoryName(mediaDriverContext.aeronDirectoryName());

    Archive.Context archiveContext =
        new Archive.Context()
            .errorHandler(ex -> logger.error("Exception occurred at Archive: ", ex))
            .maxCatalogEntries(Configurations.MAX_CATALOG_ENTRIES)
            .aeronDirectoryName(mediaDriverContext.aeronDirectoryName())
            .archiveDir(new File(nodeDirName, "archive"))
            .controlChannel(aeronArchiveContext.controlRequestChannel())
            .controlStreamId(aeronArchiveContext.controlRequestStreamId())
            .localControlStreamId(aeronArchiveContext.controlRequestStreamId())
            .recordingEventsChannel(aeronArchiveContext.recordingEventsChannel());

    ConsensusModule.Context consensusModuleContext =
        new ConsensusModule.Context()
            .errorHandler(ex -> logger.error("Exception occurred at ConsensusModule: ", ex))
            .terminationHook(() -> logger.info("TerminationHook called on ConsensusModule"))
            .aeronDirectoryName(mediaDriverContext.aeronDirectoryName())
            .clusterDir(new File(nodeDirName, "consensus"))
            .archiveContext(aeronArchiveContext.clone());

    ClusteredMediaDriver clusteredMediaDriver =
        ClusteredMediaDriver.launch(mediaDriverContext, archiveContext, consensusModuleContext);

    ClusteredService clusteredService =
        new ClusteredServiceImpl(clusteredMediaDriver.mediaDriver().context().countersManager());

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
