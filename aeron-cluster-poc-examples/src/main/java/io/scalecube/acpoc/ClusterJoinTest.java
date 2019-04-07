package io.scalecube.acpoc;

import io.aeron.CommonContext;
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
import io.scalecube.acpoc.service.EchoService;

public class ClusterJoinTest {

  private static final long MAX_CATALOG_ENTRIES = 1024;

  /**
   * Main function runner.
   *
   * @param args arguments
   */
  public static void main(String[] args) {

    String instanceId = System.getProperty("aeron.cluster.member.id", "0");

    String baseDirName =
        CommonContext.getAeronDirectoryName() + "-" + instanceId + "-" + System.currentTimeMillis();

    String aeronDirName = baseDirName + "/media";
//    String archiveDir = baseDirName + "/archive";
//    String clusterDir = baseDirName + "/cluster";
//    String clusterServiceDir = baseDirName + "/service";

    AeronArchive.Context aeronArchiveContext = new AeronArchive.Context()
        // .controlRequestChannel("aeron:udp?endpoint=localhost:8011")
        // .controlResponseChannel("aeron:udp?endpoint=localhost:8012")
        // .recordingEventsChannel(
        // "aeron:udp?control-mode=dynamic|control=localhost:8013")
        .aeronDirectoryName(baseDirName);

    MediaDriver.Context mediaDriverContest =
        new Context() //
            .aeronDirectoryName(aeronDirName)
            .errorHandler(System.err::println)
            .threadingMode(ThreadingMode.SHARED)
            .multicastFlowControlSupplier(new MinMulticastFlowControlSupplier())
            .dirDeleteOnStart(true);

    boolean cleanStart = true;

    Archive.Context archiveContext =
        new Archive.Context()
            .maxCatalogEntries(MAX_CATALOG_ENTRIES)
            .aeronDirectoryName(aeronDirName)
            .archiveDir(new File(baseDirName, "archive"))
            .controlChannel(aeronArchiveContext.controlRequestChannel())
            .controlStreamId(aeronArchiveContext.controlRequestStreamId())
            .localControlStreamId(aeronArchiveContext.controlRequestStreamId())
            .recordingEventsChannel(aeronArchiveContext.recordingEventsChannel())
            .threadingMode(ArchiveThreadingMode.SHARED)
            .deleteArchiveOnStart(cleanStart);

    ConsensusModule.Context consensusModuleCtx =
        new ConsensusModule.Context()
            .errorHandler(System.err::println)
//            .appointedLeaderId(1)
            .aeronDirectoryName(aeronDirName)
            .clusterDir(new File(baseDirName, "consensus-module"))
            .archiveContext(aeronArchiveContext.clone())
            .deleteDirOnStart(cleanStart);

    ClusteredService clusteredService = new EchoService();

    ClusteredServiceContainer.Context clusteredServiceCtx =
        new ClusteredServiceContainer.Context()
            .aeronDirectoryName(aeronDirName)
            .archiveContext(aeronArchiveContext.clone())
            .clusterDir(new File(baseDirName, "service"))
            .clusteredService(clusteredService)
            .errorHandler(System.err::println);

    ClusteredMediaDriver clusteredMediaDriver =
        ClusteredMediaDriver.launch(mediaDriverContest, archiveContext, consensusModuleCtx);

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
