package io.scalecube.acpoc;

import io.aeron.archive.client.AeronArchive;
import io.aeron.cluster.ClusteredMediaDriver;
import io.aeron.cluster.ConsensusModule.Configuration;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.cluster.service.ClusteredServiceContainer;
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
    String clusterMemberId = Integer.toHexString(Configuration.clusterMemberId());
    String nodeId = "node-" + clusterMemberId + "-" + Utils.instanceId();
    String volumeDir = Paths.get(Configurations.VOLUME_DIR, "aeron", "cluster", nodeId).toString();
    String aeronDirectoryName = Paths.get(volumeDir, "media").toString();

    System.out.println("Volume directory: " + volumeDir);
    System.out.println("Aeron directory: " + aeronDirectoryName);

    ClusteredMediaDriver clusteredMediaDriver =
        Configurations.CLUSTERED_MEDIA_DRIVER_EMBEDDED
            ? ClusteredMediaDriverRunner.launchClusteredMediaDriver(aeronDirectoryName, volumeDir)
            : null;

    ClusteredService clusteredService = new ClusteredServiceImpl();

    ClusteredServiceContainer.Context clusteredServiceCtx =
        new ClusteredServiceContainer.Context()
            .errorHandler(ex -> logger.error("Exception occurred: " + ex, ex))
            .aeronDirectoryName(aeronDirectoryName)
            .archiveContext(new AeronArchive.Context().aeronDirectoryName(aeronDirectoryName))
            .clusterDir(new File(volumeDir, "service"))
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
