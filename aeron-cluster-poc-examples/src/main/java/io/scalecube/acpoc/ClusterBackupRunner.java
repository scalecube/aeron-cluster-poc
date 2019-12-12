package io.scalecube.acpoc;

import static io.scalecube.acpoc.Configurations.CLEAN_SHUTDOWN;
import static io.scalecube.acpoc.Configurations.CLEAN_START;

import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.archive.client.AeronArchive;
import io.aeron.cluster.ClusterBackup;
import io.aeron.cluster.ClusterBackupMediaDriver;
import io.aeron.driver.DefaultAllowTerminationValidator;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.MinMulticastFlowControlSupplier;
import io.aeron.driver.ThreadingMode;
import java.io.File;
import java.nio.file.Paths;
import org.agrona.CloseHelper;
import org.agrona.IoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class ClusterBackupRunner {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClusterBackupRunner.class);

  /**
   * Main function runner.
   *
   * @param args arguments
   */
  public static void main(String[] args) {
    String nodeId = "cluster-backup-" + Runners.instanceId();
    String nodeDirName = Paths.get("target", "aeron", "cluster", nodeId).toString();

    if (CLEAN_START) {
      IoUtil.delete(new File(nodeDirName), true);
    }

    LOGGER.info("Cluster node directory: " + nodeDirName);

    String aeronDirectoryName = Paths.get(nodeDirName, "media").toString();

    MediaDriver.Context mediaDriverContext =
        new MediaDriver.Context()
            .aeronDirectoryName(aeronDirectoryName)
            .dirDeleteOnStart(true)
            .errorHandler(ex -> LOGGER.error("Exception occurred at MediaDriver: ", ex))
            .multicastFlowControlSupplier(new MinMulticastFlowControlSupplier())
            .terminationHook(() -> LOGGER.info("TerminationHook called on MediaDriver "))
            .terminationValidator(new DefaultAllowTerminationValidator())
            .threadingMode(ThreadingMode.SHARED)
            .warnIfDirectoryExists(true);

    AeronArchive.Context aeronArchiveContext =
        new AeronArchive.Context().aeronDirectoryName(aeronDirectoryName);

    Archive.Context archiveContext =
        new Archive.Context()
            .aeronDirectoryName(aeronDirectoryName)
            .archiveDir(new File(nodeDirName, "archive"))
            .controlChannel(aeronArchiveContext.controlRequestChannel())
            .controlStreamId(aeronArchiveContext.controlRequestStreamId())
            .errorHandler(ex -> LOGGER.error("Exception occurred at Archive: ", ex))
            .localControlStreamId(aeronArchiveContext.controlRequestStreamId())
            .maxCatalogEntries(Configurations.MAX_CATALOG_ENTRIES)
            .recordingEventsChannel(aeronArchiveContext.recordingEventsChannel())
            .threadingMode(ArchiveThreadingMode.SHARED);

    ClusterBackup.Context clusterBackupContext =
        new ClusterBackup.Context()
            .aeronDirectoryName(aeronDirectoryName)
            .archiveContext(aeronArchiveContext.clone())
            .clusterDir(new File(nodeDirName, "cluster-backup"))
            .eventsListener(new ClusterBackupEventsListenerImpl());

    ClusterBackupMediaDriver clusterBackupMediaDriver =
        ClusterBackupMediaDriver.launch(mediaDriverContext, archiveContext, clusterBackupContext);

    Mono<Void> onShutdown =
        Runners.onShutdown(
            () -> {
              CloseHelper.close(clusterBackupMediaDriver);
              if (CLEAN_SHUTDOWN) {
                IoUtil.delete(new File(nodeDirName), true);
              }
              return null;
            });
    onShutdown.block();
  }
}
