package io.scalecube.acpoc;

public class RecordingDescriptor {

  public final long controlSessionId;
  public final long correlationId;
  public final long recordingId;
  public final long startTimestamp;
  public final long stopTimestamp;
  public final long startPosition;
  public final long stopPosition;
  public final int initialTermId;
  public final int segmentFileLength;
  public final int termBufferLength;
  public final int mtuLength;
  public final int sessionId;
  public final int streamId;
  public final String strippedChannel;
  public final String originalChannel;
  public final String sourceIdentity;

  /**
   * Constructor.
   *
   * @param controlSessionId of the originating session requesting to list recordings.
   * @param correlationId of the associated request to list recordings.
   * @param recordingId of this recording descriptor.
   * @param startTimestamp for the recording.
   * @param stopTimestamp for the recording.
   * @param startPosition for the recording against the recorded publication.
   * @param stopPosition reached for the recording.
   * @param initialTermId for the recorded publication.
   * @param segmentFileLength for the recording which is a multiple of termBufferLength.
   * @param termBufferLength for the recorded publication.
   * @param mtuLength for the recorded publication.
   * @param sessionId for the recorded publication.
   * @param streamId for the recorded publication.
   * @param strippedChannel for the recorded publication.
   * @param originalChannel for the recorded publication.
   * @param sourceIdentity for the recorded publication.
   */
  public RecordingDescriptor(
      long controlSessionId,
      long correlationId,
      long recordingId,
      long startTimestamp,
      long stopTimestamp,
      long startPosition,
      long stopPosition,
      int initialTermId,
      int segmentFileLength,
      int termBufferLength,
      int mtuLength,
      int sessionId,
      int streamId,
      String strippedChannel,
      String originalChannel,
      String sourceIdentity) {
    this.controlSessionId = controlSessionId;
    this.correlationId = correlationId;
    this.recordingId = recordingId;
    this.startTimestamp = startTimestamp;
    this.stopTimestamp = stopTimestamp;
    this.startPosition = startPosition;
    this.stopPosition = stopPosition;
    this.initialTermId = initialTermId;
    this.segmentFileLength = segmentFileLength;
    this.termBufferLength = termBufferLength;
    this.mtuLength = mtuLength;
    this.sessionId = sessionId;
    this.streamId = streamId;
    this.strippedChannel = strippedChannel;
    this.originalChannel = originalChannel;
    this.sourceIdentity = sourceIdentity;
  }

  @Override
  public String toString() {
    return "RecordingDescriptor{"
        + "controlSessionId="
        + controlSessionId
        + ", correlationId="
        + correlationId
        + ", recordingId="
        + recordingId
        + ", startTimestamp="
        + startTimestamp
        + ", stopTimestamp="
        + stopTimestamp
        + ", startPosition="
        + startPosition
        + ", stopPosition="
        + stopPosition
        + ", initialTermId="
        + initialTermId
        + ", segmentFileLength="
        + segmentFileLength
        + ", termBufferLength="
        + termBufferLength
        + ", mtuLength="
        + mtuLength
        + ", sessionId="
        + sessionId
        + ", streamId="
        + streamId
        + ", strippedChannel='"
        + strippedChannel
        + '\''
        + ", originalChannel='"
        + originalChannel
        + '\''
        + ", sourceIdentity='"
        + sourceIdentity
        + '\''
        + '}';
  }
}
