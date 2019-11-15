package io.scalecube.arpoc;

import static io.aeron.Aeron.NULL_VALUE;

import io.aeron.Publication;
import io.aeron.archive.client.RecordingSignalAdapter;
import io.aeron.archive.codecs.RecordingSignal;
import io.aeron.archive.status.RecordingPos;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.collections.MutableReference;
import org.agrona.concurrent.status.CountersReader;

public class Utils {
  private Utils() {}

  public static int awaitRecordingCounterId(final CountersReader counters, final int sessionId) {
    int counterId;
    while (NULL_VALUE == (counterId = RecordingPos.findCounterIdBySession(counters, sessionId))) {
      Thread.yield();
    }

    return counterId;
  }

  public static void awaitPosition(
      final CountersReader counters, final int counterId, final long position) {

    while (counters.getCounterValue(counterId) < position) {
      if (counters.getCounterState(counterId) != CountersReader.RECORD_ALLOCATED) {
        throw new IllegalStateException("count not active: " + counterId);
      }

      Thread.yield();
    }
  }

  public static void offer(final Publication publication, final int index, final String prefix) {
    final String message = prefix + index;
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    final int length = buffer.putStringWithoutLengthAscii(0, message);

    while (publication.offer(buffer, 0, length) <= 0) {
      Thread.yield();
    }
  }

  public static void offerMessages(
      final Publication publication, final int startIndex, final int count, final String prefix) {

    for (int i = startIndex; i < (startIndex + count); i++) {
      offer(publication, i, prefix);
    }
  }

  public static void pollForSignal(final RecordingSignalAdapter recordingSignalAdapter) {
    while (0 == recordingSignalAdapter.poll()) {
      Thread.yield();
    }
  }

  public static void awaitSignal(
      final MutableReference<RecordingSignal> signalRef, final RecordingSignalAdapter adapter) {
    signalRef.set(null);

    do {
      pollForSignal(adapter);
    } while (signalRef.get() == null);
  }

  public static void offerToPosition(
      final Publication publication, final String prefix, final long minimumPosition) {
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();

    for (int i = 0; publication.position() < minimumPosition; i++) {
      final int length = buffer.putStringWithoutLengthAscii(0, prefix + i);

      while (publication.offer(buffer, 0, length) <= 0) {
        Thread.yield();
      }
    }
  }
}
