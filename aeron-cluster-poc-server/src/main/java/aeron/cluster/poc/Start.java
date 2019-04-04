package aeron.cluster.poc;

import io.aeron.Image;
import io.aeron.Publication;
import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.archive.client.AeronArchive;
import io.aeron.cluster.ClusteredMediaDriver;
import io.aeron.cluster.ConsensusModule;
import io.aeron.cluster.codecs.CloseReason;
import io.aeron.cluster.service.ClientSession;
import io.aeron.cluster.service.Cluster;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.cluster.service.ClusteredServiceContainer;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.MinMulticastFlowControlSupplier;
import io.aeron.driver.ThreadingMode;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class Start {

  private static final Logger logger = LoggerFactory.getLogger(Start.class);

  private static final int CLUSTER_MEMBER_ID = 1;

  private static final int EXPECTED_MEMBER_COUNT = 3;

  private static final String ARCHIVE_CONTROL_REQUEST_CHANNEL =
      "aeron:udp?term-length=64k|endpoint=localhost:801" + CLUSTER_MEMBER_ID;
  private static final String ARCHIVE_CONTROL_RESPONSE_CHANNEL =
      "aeron:udp?term-length=64k|endpoint=localhost:802" + CLUSTER_MEMBER_ID;
  private static final String RECORDING_EVENTS_CHANNEL =
      "aeron:udp?control-mode=dynamic|control=localhost:803" + CLUSTER_MEMBER_ID;

  private static final String LOG_CHANNEL =
      "aeron:udp?term-length=256k|control-mode=manual|control=localhost:5555" + CLUSTER_MEMBER_ID;

  private static final int ARCHIVE_CONTROL_REQUEST_STREAM_ID = 100 + CLUSTER_MEMBER_ID;
  private static final int ARCHIVE_CONTROL_RESPONSE_STREAM_ID = 200 + CLUSTER_MEMBER_ID;

  private static final String CLUSTER_MEMBERS;

  private static final int MAX_CATALOG_ENTRIES = 1024;

  /*
   <code>
       0,client-facing:port,member-facing:port,log:port,transfer:port,archive:port| \
       1,client-facing:port,member-facing:port,log:port,transfer:port,archive:port| ...
   </code>
  */
  static {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < EXPECTED_MEMBER_COUNT; i++) {
      String client = "localhost:2011" + i;
      String member = "localhost:2022" + i;
      String log = "localhost:2033" + i;
      String transfer = "localhost:2044" + i;
      String archive = "localhost:801" + i;
      builder.append(
          String.format("%d,%s,%s,%s,%s,%s|", i, client, member, log, transfer, archive));
    }
    builder.setLength(builder.length() - 1);
    CLUSTER_MEMBERS = builder.toString();
    logger.info("cluster members: {}", CLUSTER_MEMBERS);
  }

  public static void main(String[] args) throws InterruptedException {

    String baseDirName = Utils.tmpFileName("aeron");
    String aeronDirName = baseDirName + "-driver";
    File archiveDir = new File(baseDirName, "archive");
    File clusterDir = new File(baseDirName, "consensus-module");
    File clusterServiceDir = new File(baseDirName, "service");

    AeronArchive.Context aeronArchiveCtx =
        new AeronArchive.Context()
            .aeronDirectoryName(baseDirName)
            .controlRequestChannel(ARCHIVE_CONTROL_REQUEST_CHANNEL)
            .controlRequestStreamId(ARCHIVE_CONTROL_REQUEST_STREAM_ID)
            .controlResponseChannel(ARCHIVE_CONTROL_RESPONSE_CHANNEL)
            .controlResponseStreamId(ARCHIVE_CONTROL_RESPONSE_STREAM_ID);

    MediaDriver.Context driverCtx =
        new MediaDriver.Context()
            .aeronDirectoryName(aeronDirName)
            .threadingMode(ThreadingMode.SHARED)
            .termBufferSparseFile(true)
            .multicastFlowControlSupplier(new MinMulticastFlowControlSupplier())
            .printConfigurationOnStart(true)
            .dirDeleteOnStart(true);

    Archive.Context archiveCtx =
        new Archive.Context()
            .maxCatalogEntries(MAX_CATALOG_ENTRIES)
            .aeronDirectoryName(aeronDirName)
            .archiveDir(archiveDir)
            .controlChannel(aeronArchiveCtx.controlRequestChannel())
            .controlStreamId(aeronArchiveCtx.controlRequestStreamId())
            .localControlChannel("aeron:ipc?term-length=64k")
            .localControlStreamId(aeronArchiveCtx.controlRequestStreamId())
            .recordingEventsChannel(RECORDING_EVENTS_CHANNEL)
            .threadingMode(ArchiveThreadingMode.SHARED)
            .errorHandler(Throwable::printStackTrace)
            .deleteArchiveOnStart(true);

    ConsensusModule.Context consensusModuleCtx =
        new ConsensusModule.Context()
            .clusterMemberId(CLUSTER_MEMBER_ID)
            .clusterMembers(CLUSTER_MEMBERS)
            .aeronDirectoryName(aeronDirName)
            .clusterDir(clusterDir)
            .ingressChannel("aeron:udp?term-length=64k")
            .logChannel(LOG_CHANNEL)
            .archiveContext(aeronArchiveCtx.clone())
            .deleteDirOnStart(true);

    try (ClusteredMediaDriver clusteredMediaDriver =
        ClusteredMediaDriver.launch(driverCtx, archiveCtx, consensusModuleCtx);
    //        AeronArchive aeronArchive = AeronArchive.connect(aeronArchiveCtx)
    ) {

      logger.info(
          "clusterMembers: {}", clusteredMediaDriver.consensusModule().context().clusterMembers());

      ClusteredServiceContainer.Context clusteredServiceCtx =
          new ClusteredServiceContainer.Context()
              .aeronDirectoryName(aeronDirName)
              .archiveContext(aeronArchiveCtx.clone())
              .clusterDir(clusterServiceDir)
              .clusteredService(new CounterService());

      try (ClusteredServiceContainer clusteredServiceContainer =
          ClusteredServiceContainer.launch(clusteredServiceCtx)) {

        Thread.currentThread().join();
      }
    } finally {
      Utils.removeFile(baseDirName);
      Utils.removeFile(aeronDirName);
    }
  }

  private static class CounterService implements ClusteredService {

    private static final Logger logger = LoggerFactory.getLogger(CounterService.class);
    private Cluster cluster;

    @Override
    public void onStart(Cluster cluster) {
      this.cluster = cluster;
      logger.info(
          "onStart => memberId: {}, role: {}, client-sessions: {}",
          cluster.memberId(),
          cluster.role(),
          cluster.clientSessions().size());
    }

    @Override
    public void onSessionOpen(ClientSession session, long timestampMs) {
      logger.info(
          "onSessionOpen, timestampMs: {} => sessionId: {}, channel: {}, streamId: {}",
          timestampMs,
          session.id(),
          session.responseChannel(),
          session.responseStreamId());
    }

    @Override
    public void onSessionClose(ClientSession session, long timestampMs, CloseReason closeReason) {
      logger.info(
          "onSessionClose, timestampMs: {} => sessionId: {}, channel: {}, streamId: {}, reason: {}",
          timestampMs,
          session.id(),
          session.responseChannel(),
          session.responseStreamId(),
          closeReason);
    }

    @Override
    public void onSessionMessage(
        ClientSession session,
        long timestampMs,
        DirectBuffer buffer,
        int offset,
        int length,
        Header header) {
      logger.info(
          "onSessionMessage, timestampMs: {} => sessionId: {}, position: {}, content: {}",
          timestampMs,
          session.id(),
          header.position(),
          buffer.getStringWithoutLengthAscii(offset, length));
    }

    @Override
    public void onTimerEvent(long correlationId, long timestampMs) {
      logger.info("onTimerEvent, timestampMs: {} => correlationId: {}", timestampMs, correlationId);
    }

    @Override
    public void onTakeSnapshot(Publication snapshotPublication) {
      logger.info(
          "onTakeSnapshot => publication: sessionId: {}, channel: {}, streamId: {}, position: {}",
          snapshotPublication.sessionId(),
          snapshotPublication.channel(),
          snapshotPublication.streamId(),
          snapshotPublication.position());
    }

    @Override
    public void onLoadSnapshot(Image snapshotImage) {
      logger.info(
          "onLoadSnapshot => image: sessionId: {}, channel: {}, streamId: {}, position: {}",
          snapshotImage.sessionId(),
          snapshotImage.subscription().channel(),
          snapshotImage.subscription().streamId(),
          snapshotImage.position());
    }

    @Override
    public void onRoleChange(Cluster.Role newRole) {
      logger.info("onRoleChange => new role: {}", newRole);
    }

    @Override
    public void onTerminate(Cluster cluster) {
      logger.info(
          "onTerminate => memberId: {}, role: {}, client-sessions: {}",
          cluster.memberId(),
          cluster.role(),
          cluster.clientSessions().size());
    }
  }
}
