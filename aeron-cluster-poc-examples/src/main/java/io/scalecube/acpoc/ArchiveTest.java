package io.scalecube.acpoc;

import io.aeron.Aeron;
import io.aeron.Aeron.Context;
import io.aeron.ChannelUri;
import io.aeron.CommonContext;
import io.aeron.ConcurrentPublication;
import io.aeron.Subscription;
import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.archive.client.AeronArchive;
import io.aeron.archive.codecs.SourceLocation;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.LockSupport;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArchiveTest {

  public static final Logger LOGGER = LoggerFactory.getLogger(ArchiveTest.class);

  public static final int RECORDING_STREAM_ID = 100;
  public static final int REPLAY_STREAM_ID = 200;

  /** Archive test runner. */
  public static void main(String[] args) throws Exception {
    String aeronDirectoryName =
        Paths.get(CommonContext.getAeronDirectoryName(), "archiveTest").toString();

    try (MediaDriver mediaDriver =
            MediaDriver.launch(
                new MediaDriver.Context()
                    .errorHandler(ex -> LOGGER.error("MediaDriver error: ", ex))
                    .dirDeleteOnStart(true)
                    .threadingMode(ThreadingMode.SHARED_NETWORK)
                    .aeronDirectoryName(aeronDirectoryName));
        Archive archive =
            Archive.launch(
                new Archive.Context()
                    .errorHandler(ex1 -> LOGGER.error("Archive error: ", ex1))
                    .aeronDirectoryName(aeronDirectoryName)
                    .threadingMode(ArchiveThreadingMode.SHARED));
        AeronArchive aeronArchive =
            AeronArchive.connect(
                new AeronArchive.Context()
                    .errorHandler(ex -> LOGGER.error("AeronArchive error: ", ex))
                    .aeronDirectoryName(aeronDirectoryName));
        Aeron aeron =
            Aeron.connect(
                new Context()
                    .aeronDirectoryName(aeronDirectoryName)
                    .errorHandler(ex -> LOGGER.error("Aeron error: ", ex))); ) {

      LOGGER.info("archiveDir: " + archive.context().archiveDir().getAbsolutePath());

      startNewRecording(aeronArchive, aeron);

      ConcurrentPublication replayPublication =
          aeron.addPublication(
              ChannelUri.addSessionId(CommonContext.IPC_CHANNEL, 42), REPLAY_STREAM_ID);

      String replayChannel = startNewReplay(aeronArchive, replayPublication);

      Subscription replaySubscription = aeron.addSubscription(replayChannel, REPLAY_STREAM_ID);
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

  private static String startNewReplay(
      AeronArchive aeronArchive, ConcurrentPublication replayPublication) {
    String replayChannel = replayPublication.channel();
    LOGGER.info("replayPublication: {}", replayPublication);

    RecordingDescriptor lastRecording = findLastRecording(aeronArchive);

    long replaySessionId =
        aeronArchive.startReplay(
            lastRecording.recordingId, 0, Long.MAX_VALUE, replayChannel, REPLAY_STREAM_ID);
    LOGGER.info("aeronArchive.startReplay replaySessionId: " + replaySessionId);
    return replayChannel;
  }

  private static void startNewRecording(AeronArchive aeronArchive, Aeron aeron) {
    ConcurrentPublication recordingPublication =
        aeron.addPublication(
            ChannelUri.addSessionId(CommonContext.IPC_CHANNEL, 100500), RECORDING_STREAM_ID);
    long recording =
        aeronArchive.startRecording(
            recordingPublication.channel(), RECORDING_STREAM_ID, SourceLocation.LOCAL);

    LOGGER.info("aeronArchive.startRecording: {}, pub: {}", recording, recordingPublication);
    long offer1 = recordingPublication.offer(new UnsafeBuffer(("hello world 1").getBytes()));
    long offer2 = recordingPublication.offer(new UnsafeBuffer(("hello world 2").getBytes()));
    LOGGER.info("startNewRecording: recording offer1: {}, offer2: {}", offer1, offer2);
  }

  private static RecordingDescriptor findLastRecording(AeronArchive aeronArchive) {
    RecordingDescriptor lastRecording =
        AeronArchiveUtil.findLastRecording(aeronArchive, RECORDING_STREAM_ID);
    LOGGER.info("aeronArchiveUtil.findLastRecording: " + lastRecording);
    return lastRecording;
  }
}
