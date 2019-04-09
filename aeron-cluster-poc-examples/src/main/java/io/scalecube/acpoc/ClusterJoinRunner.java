package io.scalecube.acpoc;

import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.archive.client.AeronArchive;
import io.aeron.cluster.ClusteredMediaDriver;
import io.aeron.cluster.ConsensusModule;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.cluster.service.ClusteredServiceContainer;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.MediaDriver.Context;
import io.aeron.driver.MinMulticastFlowControlSupplier;
import io.aeron.driver.ThreadingMode;
import java.io.File;
import org.agrona.CloseHelper;
import org.agrona.IoUtil;
import reactor.core.publisher.Mono;

/**
 * Main class that starts single node in cluster, though expecting most of cluster configuration
 * passed via VM args.
 */
public class ClusterJoinRunner {

  /**
   * Main function runner.
   *
   * @param args arguments
   */
  public static void main(String[] args) {

    String aeronHome = IoUtil.tmpDirName() + "aeron-cluster-" + Utils.getInstanceId();

    String aeronDirName = aeronHome + "/media";

    AeronArchive.Context aeronArchiveContext =
        new AeronArchive.Context().aeronDirectoryName(aeronDirName);

    MediaDriver.Context mediaDriverContest =
        new Context()
            .errorHandler(System.err::println)
            .aeronDirectoryName(aeronDirName)
            .threadingMode(ThreadingMode.SHARED)
            .multicastFlowControlSupplier(new MinMulticastFlowControlSupplier());

    Archive.Context archiveContext =
        new Archive.Context()
            .maxCatalogEntries(Configurations.MAX_CATALOG_ENTRIES)
            .aeronDirectoryName(aeronDirName)
            .archiveDir(new File(aeronHome, "archive"))
            .controlChannel(aeronArchiveContext.controlRequestChannel())
            .controlStreamId(aeronArchiveContext.controlRequestStreamId())
            .localControlStreamId(aeronArchiveContext.controlRequestStreamId())
            .recordingEventsChannel(aeronArchiveContext.recordingEventsChannel())
            .threadingMode(ArchiveThreadingMode.SHARED);

    ConsensusModule.Context consensusModuleCtx =
        new ConsensusModule.Context()
            .errorHandler(System.err::println)
            .aeronDirectoryName(aeronDirName)
            .clusterDir(new File(aeronHome, "consensus-module"))
            .archiveContext(aeronArchiveContext.clone());

    ClusteredMediaDriver clusteredMediaDriver =
        ClusteredMediaDriver.launch(mediaDriverContest, archiveContext, consensusModuleCtx);

    ClusteredService clusteredService =
        new ClusterService(clusteredMediaDriver.mediaDriver().context().countersManager());

    ClusteredServiceContainer.Context clusteredServiceCtx =
        new ClusteredServiceContainer.Context()
            .errorHandler(System.err::println)
            .aeronDirectoryName(aeronDirName)
            .archiveContext(aeronArchiveContext.clone())
            .clusterDir(new File(aeronHome, "service"))
            .clusteredService(clusteredService);

    ClusteredServiceContainer clusteredServiceContainer =
        ClusteredServiceContainer.launch(clusteredServiceCtx);

    Mono<Void> onShutdown =
        Utils.onShutdown(
            () -> {
              CloseHelper.close(clusteredMediaDriver);
              CloseHelper.close(clusteredServiceContainer);
              //IoUtil.delete(new File(aeronHome), true);
              return null;
            });
    onShutdown.block();
  }
}
