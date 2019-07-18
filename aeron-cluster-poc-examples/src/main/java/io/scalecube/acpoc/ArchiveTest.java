package io.scalecube.acpoc;

import static org.agrona.concurrent.status.CountersReader.NULL_COUNTER_ID;

import io.aeron.Aeron;
import io.aeron.Aeron.Context;
import io.aeron.ChannelUriStringBuilder;
import io.aeron.CommonContext;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.archive.ArchivingMediaDriver;
import io.aeron.archive.client.AeronArchive;
import io.aeron.archive.codecs.SourceLocation;
import io.aeron.archive.status.RecordingPos;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import java.io.File;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.LockSupport;
import org.agrona.IoUtil;
import org.agrona.SystemUtil;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArchiveTest {

  public static final Logger LOGGER = LoggerFactory.getLogger(ArchiveTest.class);

  private static final int TERM_BUFFER_LENGTH = 64 * 1024;

  private static final int PUBLICATION_TAG = 2;
  private static final int STREAM_ID = 33;

  private static final String CONTROL_ENDPOINT = "localhost:43265";
  private static final String RECORDING_ENDPOINT = "localhost:43266";
  private static final String REPLAY_ENDPOINT = "localhost:43268";
  private static final String LIVE_ENDPOINT = "localhost:43267";

  private static final IdleStrategy IDLE_STRATEGY = new BackoffIdleStrategy(10, 100, 1000, 1000000);

  private static final ChannelUriStringBuilder PUBLICATION_CHANNEL =
      new ChannelUriStringBuilder()
          .media(CommonContext.UDP_MEDIA)
          .tags("1," + PUBLICATION_TAG)
          .controlEndpoint(CONTROL_ENDPOINT)
          .controlMode(CommonContext.MDC_CONTROL_MODE_DYNAMIC)
          .termLength(TERM_BUFFER_LENGTH);

  private static final ChannelUriStringBuilder RECORDING_CHANNEL =
      new ChannelUriStringBuilder()
          .media(CommonContext.UDP_MEDIA)
          .endpoint(RECORDING_ENDPOINT)
          .controlEndpoint(CONTROL_ENDPOINT);

  private static final ChannelUriStringBuilder SUBSCRIPTION_CHANNEL =
      new ChannelUriStringBuilder()
          .media(CommonContext.UDP_MEDIA)
          .controlMode(CommonContext.MDC_CONTROL_MODE_MANUAL);

  private static final ChannelUriStringBuilder liveDestination =
      new ChannelUriStringBuilder()
          .media(CommonContext.UDP_MEDIA)
          .endpoint(LIVE_ENDPOINT)
          .controlEndpoint(CONTROL_ENDPOINT);

  private static final ChannelUriStringBuilder replayDestination =
      new ChannelUriStringBuilder().media(CommonContext.UDP_MEDIA).endpoint(REPLAY_ENDPOINT);

  private static final ChannelUriStringBuilder REPLAY_CHANNEL =
      new ChannelUriStringBuilder()
          .media(CommonContext.UDP_MEDIA)
          .isSessionIdTagged(true)
          .sessionId(PUBLICATION_TAG)
          .endpoint(REPLAY_ENDPOINT);

  /** Archive test runner. */
  public static void main(String[] args) {
    final File archiveDir = new File(SystemUtil.tmpDirName(), "archive");
    IoUtil.delete(archiveDir, true);
    String aeronDirName =
        Paths.get(CommonContext.getAeronDirectoryName(), "archiveTest").toString();
    MediaDriver.Context mediaDriverContext =
        new MediaDriver.Context().aeronDirectoryName(aeronDirName);

    try (ArchivingMediaDriver archivingMediaDriver =
            ArchivingMediaDriver.launch(
                mediaDriverContext
                    .termBufferSparseFile(true)
                    .aeronDirectoryName(aeronDirName)
                    .publicationTermBufferLength(TERM_BUFFER_LENGTH)
                    .threadingMode(ThreadingMode.SHARED)
                    .errorHandler(Throwable::printStackTrace)
                    .spiesSimulateConnection(false)
                    .dirDeleteOnStart(true),
                new Archive.Context()
                    .maxCatalogEntries(1024)
                    .aeronDirectoryName(mediaDriverContext.aeronDirectoryName())
                    .errorHandler(Throwable::printStackTrace)
                    .archiveDir(archiveDir)
                    .fileSyncLevel(0)
                    .threadingMode(ArchiveThreadingMode.SHARED)
                    .deleteArchiveOnStart(true));
        Aeron aeron =
            Aeron.connect(
                new Context()
                    .aeronDirectoryName(mediaDriverContext.aeronDirectoryName())
                    .errorHandler(ex -> LOGGER.error("Aeron error: ", ex)));
        AeronArchive aeronArchive =
            AeronArchive.connect(
                new AeronArchive.Context()
                    .errorHandler(ex -> LOGGER.error("AeronArchive error: ", ex))
                    .aeron(aeron))) {

      LOGGER.info(
          "archiveDir: " + archivingMediaDriver.archive().context().archiveDir().getAbsolutePath());

      Publication recordingPub = aeron.addPublication(PUBLICATION_CHANNEL.build(), STREAM_ID);

      int sessionId = recordingPub.sessionId();
      String recordingChannel = RECORDING_CHANNEL.sessionId(sessionId).build();
      String subscriptionChannel = SUBSCRIPTION_CHANNEL.sessionId(sessionId).build();

      aeronArchive.startRecording(recordingChannel, STREAM_ID, SourceLocation.REMOTE);

      send(recordingPub, "recordingPub-1");

      RecordingDescriptor lastRecording =
          AeronArchiveUtil.findLastRecording(aeronArchive, recordingChannel, STREAM_ID);
      LOGGER.info("aeronArchiveUtil.findLastRecording: " + lastRecording);

      long replaySessionId =
          aeronArchive.startReplay(
              lastRecording.recordingId, 0, -1, REPLAY_CHANNEL.build(), STREAM_ID);

      Publication replyPub = aeron.addPublication(REPLAY_CHANNEL.build(), STREAM_ID);

      send(replyPub, "replyPub-1");

      Subscription replaySubscription = aeron.addSubscription(subscriptionChannel, STREAM_ID);
      LOGGER.info("created replaySubscription: {}, connecting ...", replaySubscription);
      do {
        LockSupport.parkNanos(1);
      } while (!replaySubscription.isConnected());
      LOGGER.info(
          "replaySubscription connected: {}, images: {}",
          replaySubscription,
          replaySubscription.images());

      CountDownLatch latch = new CountDownLatch(2);

      do {
        int poll =
            replaySubscription.poll(
                (buffer, offset, length, header) -> {
                  byte[] bytes = new byte[length];
                  buffer.getBytes(offset, bytes);
                  LOGGER.info(
                      "### (buffer, offset, length, header) -> '{}' on sessionId: {}, streamId: {}",
                      new String(bytes),
                      header.sessionId(),
                      header.streamId());
                  latch.countDown();
                },
                100500);

        if (poll > 0) {
          LOGGER.info("replaySubscription.poll fragments received: " + poll);
        }
      } while (latch.getCount() != 0);
    }
  }

  private static void send(Publication publication, String msg) {
    while (true) {
      long result = publication.offer(new UnsafeBuffer(msg.getBytes()));
      if (result > 0) {
        break;
      }
      LOGGER.warn("couldn't send by [{}] from [{}]: {}", result, publication.channel(), msg);
      IDLE_STRATEGY.idle();
    }
    LOGGER.info("sent from {}: {}", publication.channel(), msg);
  }

  private static int awaitCounterId(final CountersReader counters, final int sessionId) {
    int counterId;

    while (NULL_COUNTER_ID
        == (counterId = RecordingPos.findCounterIdBySession(counters, sessionId))) {
      Thread.yield();
    }

    return counterId;
  }
}
