package io.scalecube.acpoc.benchmarks;

import io.aeron.archive.Archive;
import io.aeron.archive.client.AeronArchive;
import io.aeron.cluster.ClusteredMediaDriver;
import io.aeron.cluster.ConsensusModule;
import io.aeron.cluster.ConsensusModule.Configuration;
import io.aeron.cluster.service.ClusteredServiceContainer;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.MinMulticastFlowControlSupplier;
import io.scalecube.acpoc.Configurations;
import io.scalecube.acpoc.Utils;
import java.io.File;
import java.nio.file.Paths;
import org.agrona.CloseHelper;
import org.agrona.IoUtil;
import reactor.core.publisher.Mono;

/**
 * Main class that starts single node in cluster, though expecting most of cluster configuration
 * passed via VM args.
 */
public class ClusteredServiceRunner {

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

    MediaDriver.Context mediaDriverContest =
        new MediaDriver.Context()
            .warnIfDirectoryExists(true)
            .dirDeleteOnStart(true)
            .dirDeleteOnShutdown(true)
            .printConfigurationOnStart(true)
            .errorHandler(Throwable::printStackTrace)
            .multicastFlowControlSupplier(new MinMulticastFlowControlSupplier());

    AeronArchive.Context aeronArchiveContext =
        new AeronArchive.Context().aeronDirectoryName(mediaDriverContest.aeronDirectoryName());

    Archive.Context archiveContext =
        new Archive.Context()
            .maxCatalogEntries(Configurations.MAX_CATALOG_ENTRIES)
            .deleteArchiveOnStart(true)
            .aeronDirectoryName(mediaDriverContest.aeronDirectoryName())
            .archiveDir(new File(nodeDirName, "archive"))
            .controlChannel(aeronArchiveContext.controlRequestChannel())
            .controlStreamId(aeronArchiveContext.controlRequestStreamId())
            .localControlStreamId(aeronArchiveContext.controlRequestStreamId())
            .recordingEventsChannel(aeronArchiveContext.recordingEventsChannel());

    ConsensusModule.Context consensusModuleCtx =
        new ConsensusModule.Context()
            .errorHandler(Throwable::printStackTrace)
            .aeronDirectoryName(mediaDriverContest.aeronDirectoryName())
            .clusterDir(new File(nodeDirName, "consensus-module"))
            .archiveContext(aeronArchiveContext.clone());

    ClusteredMediaDriver clusteredMediaDriver =
        ClusteredMediaDriver.launch(mediaDriverContest, archiveContext, consensusModuleCtx);

    ClusteredServiceContainer.Context clusteredServiceCtx =
        new ClusteredServiceContainer.Context()
            .errorHandler(Throwable::printStackTrace)
            .aeronDirectoryName(clusteredMediaDriver.mediaDriver().aeronDirectoryName())
            .archiveContext(aeronArchiveContext.clone())
            .clusterDir(new File(nodeDirName, "service"))
            .clusteredService(new BenchmarkClusteredService());

    ClusteredServiceContainer clusteredServiceContainer =
        ClusteredServiceContainer.launch(clusteredServiceCtx);

    Mono<Void> onShutdown =
        Utils.onShutdown(
            () -> {
              CloseHelper.quietClose(clusteredMediaDriver);
              CloseHelper.quietClose(clusteredServiceContainer);
              if (Configurations.CLEAN_SHUTDOWN) {
                IoUtil.delete(new File(nodeDirName), true);
              }
              return null;
            });
    onShutdown.block();
  }
}
