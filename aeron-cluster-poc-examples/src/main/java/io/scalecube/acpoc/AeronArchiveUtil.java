package io.scalecube.acpoc;

import io.aeron.archive.client.AeronArchive;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AeronArchiveUtil {

  public static final Logger LOGGER = LoggerFactory.getLogger(AeronArchiveUtil.class);

  private AeronArchiveUtil() {
    // Do not instantiate
  }

  /**
   * Finds last {@link RecordingDescriptor} by given stream id. See for details {@link
   * AeronArchive#listRecordingsForUri(long, int, String, int,
   * io.aeron.archive.client.RecordingDescriptorConsumer)}.
   *
   * @param aeronArchive aeron archive
   * @param channel channel
   * @param streamId stream id
   * @return last recording descriptor.
   */
  public static RecordingDescriptor findLastRecording(
      AeronArchive aeronArchive, String channel, int streamId) {
    AtomicReference<RecordingDescriptor> result = new AtomicReference<>();
    int count =
        aeronArchive.listRecordingsForUri(
            0,
            Integer.MAX_VALUE,
            channel,
            streamId,
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
                streamId1,
                strippedChannel,
                originalChannel,
                sourceIdentity) -> {
              result.set(
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
                      streamId1,
                      strippedChannel,
                      originalChannel,
                      sourceIdentity));
            });
    LOGGER.debug("findLastRecording(streamId={}), count={}", streamId, count);
    return Optional.ofNullable(result.get()).orElse(null);
  }
}
