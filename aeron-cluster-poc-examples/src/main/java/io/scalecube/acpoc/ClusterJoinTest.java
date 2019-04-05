package io.scalecube.acpoc;

import io.aeron.CommonContext;
import io.aeron.archive.Archive;
import io.aeron.archive.client.AeronArchive;
import io.aeron.cluster.ClusteredMediaDriver;
import io.aeron.cluster.ConsensusModule;
import io.aeron.cluster.service.ClusteredServiceContainer;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.MediaDriver.Context;
import java.io.File;

public class ClusterJoinTest {

  /**
   * Main function runner.
   *
   * @param args arguments
   */
  public static void main(String[] args) {
    String aeronHome = CommonContext.getAeronDirectoryName() + "-" + System.currentTimeMillis();

    String mediaDir = aeronHome + "/media";
    String archiveDir = aeronHome + "/archive";
    String clusterDir = aeronHome + "/cluster";
    String clusterServiceDir = aeronHome + "/service";

    MediaDriver.Context driverCtx =
        new Context() //
            .errorHandler(System.err::println)
            .aeronDirectoryName(mediaDir);

    Archive.Context archiveCtx =
        new Archive.Context()
            // .controlChannel("aeron:udp?endpoint=localhost:8011")
            // .recordingEventsChannel("aeron:udp?control-mode=dynamic|control=localhost:8013")
            .errorHandler(System.err::println)
            .aeronDirectoryName(mediaDir)
            .archiveDirectoryName(archiveDir);

    AeronArchive.Context aeronArchiveContext = new AeronArchive.Context()
        // .controlRequestChannel("aeron:udp?endpoint=localhost:8011")
        // .controlResponseChannel("aeron:udp?endpoint=localhost:8012")
        // .recordingEventsChannel(
        // "aeron:udp?control-mode=dynamic|control=localhost:8013")
        ;

    ConsensusModule.Context consensusModuleCtx =
        new ConsensusModule.Context()
            .archiveContext(aeronArchiveContext.clone())
            .errorHandler(System.err::println)
            .aeronDirectoryName(mediaDir)
            .clusterDirectoryName(clusterDir);

    ClusteredMediaDriver clusteredMediaDriver =
        ClusteredMediaDriver.launch(driverCtx, archiveCtx, consensusModuleCtx);

    ClusteredServiceContainer.Context clusteredServiceCtx =
        new ClusteredServiceContainer.Context()
            .errorHandler(System.err::println)
            .aeronDirectoryName(mediaDir)
            .archiveContext(aeronArchiveContext.clone())
            .clusterDir(new File(clusterServiceDir))
            .clusteredService(new CounterService());

    ClusteredServiceContainer clusteredServiceContainer =
        ClusteredServiceContainer.launch(clusteredServiceCtx);

    clusteredMediaDriver //
        .consensusModule()
        .context()
        .shutdownSignalBarrier()
        .await();

    clusteredMediaDriver.close();
  }
}
