package io.scalecube.arpoc;

import static io.aeron.Aeron.NULL_VALUE;

import io.aeron.archive.status.RecordingPos;
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
}
