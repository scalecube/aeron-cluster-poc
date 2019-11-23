package io.scalecube.acpoc;

import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.archive.client.AeronArchive;
import io.aeron.cluster.ClusteredMediaDriver;
import io.aeron.cluster.ConsensusModule;
import io.aeron.cluster.ConsensusModule.Configuration;
import io.aeron.driver.DefaultAllowTerminationValidator;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.MinMulticastFlowControlSupplier;
import io.aeron.driver.ThreadingMode;
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
public class ClusteredMediaDriverRunner {

  private static final Logger logger = LoggerFactory.getLogger(ClusteredMediaDriverRunner.class);

  /**
   * Main function runner.
   *
   * @param args arguments
   */
  public static void main(String[] args) {
    String clusterMemberId = Integer.toHexString(Configuration.clusterMemberId());
    String nodeId = "node-" + clusterMemberId + "-" + Utils.instanceId();
    String volumeDir = Paths.get(Configurations.VOLUME_DIR, "aeron", "cluster", nodeId).toString();
    String aeronDirectoryName = Paths.get(volumeDir, "media").toString();

    System.out.println("Volume directory: " + volumeDir);
    System.out.println("Aeron directory: " + aeronDirectoryName);

    ClusteredMediaDriver clusteredMediaDriver =
        launchClusteredMediaDriver(aeronDirectoryName, volumeDir);

    Mono<Void> onShutdown =
        Utils.onShutdown(
            () -> {
              CloseHelper.close(clusteredMediaDriver);
              return null;
            });
    onShutdown.block();
  }

  static ClusteredMediaDriver launchClusteredMediaDriver(
      String aeronDirectoryName, String volumeDir) {
    AeronArchive.Context aeronArchiveContext =
        new AeronArchive.Context().aeronDirectoryName(aeronDirectoryName);

    MediaDriver.Context mediaDriverContext =
        new MediaDriver.Context()
            .errorHandler(ex -> logger.error("Exception occurred at MediaDriver: ", ex))
            .terminationHook(() -> logger.info("TerminationHook called on MediaDriver "))
            .terminationValidator(new DefaultAllowTerminationValidator())
            .threadingMode(ThreadingMode.SHARED)
            .warnIfDirectoryExists(true)
            .dirDeleteOnStart(true)
            .aeronDirectoryName(aeronDirectoryName)
            .multicastFlowControlSupplier(new MinMulticastFlowControlSupplier());

    Archive.Context archiveContext =
        new Archive.Context()
            .errorHandler(ex -> logger.error("Exception occurred at Archive: ", ex))
            .maxCatalogEntries(Configurations.MAX_CATALOG_ENTRIES)
            .aeronDirectoryName(aeronDirectoryName)
            .archiveDir(new File(volumeDir, "archive"))
            .controlChannel(aeronArchiveContext.controlRequestChannel())
            .controlStreamId(aeronArchiveContext.controlRequestStreamId())
            .localControlStreamId(aeronArchiveContext.controlRequestStreamId())
            .recordingEventsChannel(aeronArchiveContext.recordingEventsChannel())
            .threadingMode(ArchiveThreadingMode.SHARED);

    ConsensusModule.Context consensusModuleContext =
        new ConsensusModule.Context()
            .errorHandler(ex -> logger.error("Exception occurred at ConsensusModule: ", ex))
            .terminationHook(() -> logger.info("TerminationHook called on ConsensusModule"))
            .aeronDirectoryName(aeronDirectoryName)
            .clusterDir(new File(volumeDir, "consensus"))
            .archiveContext(aeronArchiveContext.clone());

    return ClusteredMediaDriver.launch(mediaDriverContext, archiveContext, consensusModuleContext);
  }
}
