package io.scalecube.acpoc;

import io.aeron.ChannelUri;
import io.aeron.ChannelUriStringBuilder;
import io.aeron.CommonContext;
import io.aeron.Publication;
import io.aeron.archive.client.AeronArchive;
import io.aeron.archive.codecs.SourceLocation;
import io.aeron.cluster.service.Cluster;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventRecorder {

  private static final Logger logger = LoggerFactory.getLogger(EventRecorder.class);

  private static final int EVENT_STREAM_ID = 12123;

  private final MutableDirectBuffer buffer = new UnsafeBuffer();
  private final Publication publication;
  private final IdleStrategy idleStrategy;

  private long recordingPosition;

  /** Create an event recorder} to publish events. */
  public static EventRecorder createEventRecorder(
      AeronArchive aeronArchive, Cluster cluster, long recordingPosition) {
    RecordingDescriptor lastRecording =
        AeronArchiveUtil.findLastRecording(aeronArchive, EVENT_STREAM_ID);

    if (lastRecording == null && recordingPosition > 0) {
      // todo need to catch up missing events from other nodes (discovery)
      logger.error("eventPosition = {} but lastRecording wasn't found", recordingPosition);
      throw new IllegalStateException("eventPosition > 0 but lastRecording wasn't found");
    }

    if (lastRecording != null && recordingPosition > lastRecording.stopPosition) {
      // todo need to catch up missing events from other nodes (discovery)
      logger.error(
          "lastRecording.stopPosition = {} less than eventPosition = {}",
          lastRecording.stopPosition,
          recordingPosition);
      throw new IllegalStateException("lastRecording.stopPosition < eventPosition");
    }

    Publication orderEventPublication;

    if (lastRecording == null && recordingPosition == 0) {
      // Start new recording
      orderEventPublication =
          cluster.aeron().addExclusivePublication(CommonContext.IPC_CHANNEL, EVENT_STREAM_ID);
      String channel =
          ChannelUri.addSessionId(CommonContext.IPC_CHANNEL, orderEventPublication.sessionId());
      aeronArchive.startRecording(channel, EVENT_STREAM_ID, SourceLocation.LOCAL);
    } else {
      // Continue recording
      long stopPosition = lastRecording.stopPosition;
      if (recordingPosition < stopPosition) {
        aeronArchive.truncateRecording(lastRecording.recordingId, recordingPosition);
        stopPosition = recordingPosition;
      }

      ChannelUriStringBuilder channelBuilder =
          new ChannelUriStringBuilder()
              .media(CommonContext.IPC_MEDIA)
              .mtu(lastRecording.mtuLength)
              .initialPosition(
                  stopPosition, lastRecording.initialTermId, lastRecording.termBufferLength);

      orderEventPublication =
          cluster.aeron().addExclusivePublication(channelBuilder.build(), EVENT_STREAM_ID);

      String channel = channelBuilder.sessionId(orderEventPublication.sessionId()).build();
      aeronArchive.extendRecording(
          lastRecording.recordingId, channel, EVENT_STREAM_ID, SourceLocation.LOCAL);
    }

    return new EventRecorder(orderEventPublication, cluster);
  }

  private EventRecorder(Publication publication, IdleStrategy idleStrategy) {
    this.publication = publication;
    this.idleStrategy = idleStrategy;
  }

  /** Records event in aeron archive. The command will process until record event successfully. */
  public void recordEvent(byte[] content) {
    buffer.wrap(content);
    while (true) {
      long result = publication.offer(buffer);
      if (result > 0) {
        recordingPosition = result;
        break;
      }
      checkResultAndIdle(result);
    }
  }

  /**
   * Returns event recording position.
   *
   * @param aeronArchive aeron archive
   * @return event recording position
   */
  public long recordingPosition(AeronArchive aeronArchive) {
    RecordingDescriptor lastRecording =
        AeronArchiveUtil.findLastRecording(aeronArchive, EVENT_STREAM_ID);

    if (lastRecording == null) {
      throw new IllegalStateException("Not expecting not-existing lastRecording at onTakeSnapshot");
    }
    long foundRecordingPosition = aeronArchive.getRecordingPosition(lastRecording.recordingId);
    logger.info(
        "recordingPosition: {}, foundRecordingPosition: {}",
        recordingPosition,
        foundRecordingPosition);
    return foundRecordingPosition;
  }

  private void checkResultAndIdle(long result) {
    checkResult(result);
    Utils.checkInterruptedStatus();
    idleStrategy.idle();
  }

  private void checkResult(final long result) {
    if (result == Publication.NOT_CONNECTED
        || result == Publication.CLOSED
        || result == Publication.MAX_POSITION_EXCEEDED) {
      Utils.fail("unexpected order event publication state: " + result);
    }
  }
}
