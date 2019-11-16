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

  public static final int EVENT_STREAM_ID = 12123;

  private final MutableDirectBuffer buffer = new UnsafeBuffer();
  private final Publication publication;
  private final IdleStrategy idleStrategy;

  private final RecordingDescriptor eventRecording;

  /** Create an event recorder} to publish events. */
  public static EventRecorder createEventRecorder(
      AeronArchive aeronArchive, Cluster cluster, RecordingDescriptor eventRecording) {
    RecordingDescriptor lastRecording =
        AeronArchiveUtil.findLastRecording(aeronArchive, EVENT_STREAM_ID);

    if (eventRecording == null) {
      eventRecording = new RecordingDescriptor();
    }

    // if (lastRecording == null && eventRecording.recordingPosition > 0) {
    //   // todo need to catch up missing events from other nodes (discovery)
    //   logger.error("eventPosition = {} but lastRecording wasn't found",
    // eventRecording.recordingPosition);
    //   throw new IllegalStateException("eventPosition > 0 but lastRecording wasn't found");
    // }

    if (lastRecording != null && eventRecording.recordingPosition > lastRecording.stopPosition) {
      // todo need to catch up missing events from other nodes (discovery)
      logger.error(
          "lastRecording.stopPosition = {} less than eventPosition = {}",
          lastRecording.stopPosition,
          eventRecording.recordingPosition);
      throw new IllegalStateException("lastRecording.stopPosition < eventPosition");
    }

    Publication orderEventPublication;

    if (lastRecording == null && eventRecording.recordingPosition == 0) {
      // Start new recording
      orderEventPublication =
          cluster.aeron().addExclusivePublication(CommonContext.IPC_CHANNEL, EVENT_STREAM_ID);
      String channel =
          ChannelUri.addSessionId(CommonContext.IPC_CHANNEL, orderEventPublication.sessionId());
      aeronArchive.startRecording(channel, EVENT_STREAM_ID, SourceLocation.LOCAL);
    } else if (lastRecording == null && eventRecording.recordingPosition > 0) {

      String channel0 =
          new ChannelUriStringBuilder()
              .media(CommonContext.IPC_MEDIA)
              .mtu(eventRecording.mtuLength)
              .initialPosition(
                  eventRecording.recordingPosition,
                  eventRecording.initialTermId,
                  eventRecording.termBufferLength)
              .build();

      // Start new recording
      orderEventPublication = cluster.aeron().addExclusivePublication(channel0, EVENT_STREAM_ID);
      String channel = ChannelUri.addSessionId(channel0, orderEventPublication.sessionId());
      aeronArchive.startRecording(channel, EVENT_STREAM_ID, SourceLocation.LOCAL);
    } else {
      // Continue recording
      long stopPosition = lastRecording.stopPosition;
      if (eventRecording.recordingPosition < stopPosition) {
        aeronArchive.truncateRecording(lastRecording.recordingId, eventRecording.recordingPosition);
        stopPosition = eventRecording.recordingPosition;
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

    logger.info(
        "orderEventPublication: {}",
        ChannelUri.addSessionId(
            orderEventPublication.channel(), orderEventPublication.sessionId()));

    return new EventRecorder(orderEventPublication, eventRecording, cluster);
  }

  private EventRecorder(
      Publication publication, RecordingDescriptor eventRecording, IdleStrategy idleStrategy) {
    this.publication = publication;
    this.eventRecording = eventRecording;
    this.idleStrategy = idleStrategy;
  }

  /** Records event in aeron archive. The command will process until record event successfully. */
  public void recordEvent(byte[] content) {
    logger.info("recordEvent: {}", new String(content));
    buffer.wrap(content, 0, content.length);
    while (true) {
      long result = publication.offer(buffer);
      if (result > 0) {
        eventRecording.recordingPosition = result;
        break;
      }
      checkResultAndIdle(result);
    }
  }

  /**
   * Returns event recording.
   *
   * @param aeronArchive aeron archive
   * @return event recording position
   */
  public RecordingDescriptor eventRecording(AeronArchive aeronArchive) {
    RecordingDescriptor lastRecording =
        AeronArchiveUtil.findLastRecording(aeronArchive, EVENT_STREAM_ID);

    if (lastRecording == null) {
      throw new IllegalStateException("Not expecting not-existing lastRecording at onTakeSnapshot");
    }

    long foundRecordingPosition = aeronArchive.getRecordingPosition(lastRecording.recordingId);
    lastRecording.recordingPosition = foundRecordingPosition;
    logger.info(
        "recordingPosition: {}, foundRecordingPosition: {}, recording: {}",
        eventRecording.recordingPosition,
        foundRecordingPosition,
        lastRecording);
    return lastRecording;
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
