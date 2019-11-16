package io.scalecube.acpoc;

import static io.scalecube.acpoc.EventRecorder.EVENT_STREAM_ID;

import io.aeron.CommonContext;
import io.aeron.Subscription;
import io.aeron.archive.client.AeronArchive;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.agrona.concurrent.SleepingIdleStrategy;

public class EventReplayer {

  public static void main(String[] args) {
    String aeronDirectoryName = CommonContext.generateRandomDirName();
    long position = Long.getLong("position", -1);

    try (MediaDriver mediaDriver =
            MediaDriver.launch(
                new MediaDriver.Context()
                    .aeronDirectoryName(aeronDirectoryName)
                    .termBufferSparseFile(false)
                    .threadingMode(ThreadingMode.SHARED)
                    .errorHandler(Throwable::printStackTrace)
                    .spiesSimulateConnection(false)
                    .dirDeleteOnShutdown(true)
                    .dirDeleteOnStart(true));
        AeronArchive aeronArchive =
            AeronArchive.connect(
                new AeronArchive.Context()
                    .aeronDirectoryName(aeronDirectoryName)
                    .errorHandler(Throwable::printStackTrace))) {

      printAllRecordings(aeronArchive);
      RecordingDescriptor eventRecording = lookupEventRecording(aeronArchive);

      Subscription subscription =
          aeronArchive.replay(
              eventRecording.recordingId,
              position,
              Long.MAX_VALUE,
              "aeron:udp?endpoint=localhost:40891",
              9999);
      System.out.println("waiting for events");

      SleepingIdleStrategy idleStrategy = new SleepingIdleStrategy(TimeUnit.SECONDS.toNanos(1));

      while (!Thread.currentThread().isInterrupted() && !subscription.isClosed()) {
        idleStrategy.idle(
            subscription.poll(
                (buffer, offset, length, header) -> {
                  byte[] bytes = new byte[length];
                  buffer.getBytes(offset, bytes);
                  System.out.println(new String(bytes));
                },
                5));
      }
    }
  }

  private static void printAllRecordings(AeronArchive aeronArchive) {
    System.out.println("----- all recordings -----");
    aeronArchive.listRecordings(
        0,
        Integer.MAX_VALUE,
        (controlSessionId,
            correlationId,
            recordingId,
            startTimestamp,
            stopTimestamp,
            startPosition,
            stopPosition,
            initialTermId,
            segmentFileLength,
            termBufferLength,
            mtuLength,
            sessionId,
            streamId,
            strippedChannel,
            originalChannel,
            sourceIdentity) ->
            System.out.println(
                new RecordingDescriptor(
                    controlSessionId,
                    correlationId,
                    recordingId,
                    startTimestamp,
                    stopTimestamp,
                    startPosition,
                    stopPosition,
                    initialTermId,
                    segmentFileLength,
                    termBufferLength,
                    mtuLength,
                    sessionId,
                    streamId,
                    strippedChannel,
                    originalChannel,
                    sourceIdentity)));
    System.out.println("--------------------------");
  }

  private static RecordingDescriptor lookupEventRecording(AeronArchive aeronArchive) {
    System.out.println("----- lookup event recording -----");
    AtomicReference<RecordingDescriptor> result = new AtomicReference<>();
    aeronArchive.listRecordingsForUri(
        0,
        Integer.MAX_VALUE,
        CommonContext.IPC_CHANNEL,
        EVENT_STREAM_ID,
        (controlSessionId,
            correlationId,
            recordingId,
            startTimestamp,
            stopTimestamp,
            startPosition,
            stopPosition,
            initialTermId,
            segmentFileLength,
            termBufferLength,
            mtuLength,
            sessionId,
            streamId,
            strippedChannel,
            originalChannel,
            sourceIdentity) -> {
          final RecordingDescriptor recording =
              new RecordingDescriptor(
                  controlSessionId,
                  correlationId,
                  recordingId,
                  startTimestamp,
                  stopTimestamp,
                  startPosition,
                  stopPosition,
                  initialTermId,
                  segmentFileLength,
                  termBufferLength,
                  mtuLength,
                  sessionId,
                  streamId,
                  strippedChannel,
                  originalChannel,
                  sourceIdentity);
          result.set(recording);
          System.out.println(recording);
        });
    System.out.println("--------------------------");
    return result.get();
  }
}
