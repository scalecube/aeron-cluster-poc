package io.scalecube.acpoc.benchmarks.report.throughput;

import org.agrona.CloseHelper;

public class CompositeThroughputListener implements ThroughputListener {

  private final ThroughputListener[] listeners;

  public CompositeThroughputListener(ThroughputListener... listeners) {
    this.listeners = listeners;
  }

  @Override
  public void close() {
    CloseHelper.quietCloseAll(listeners);
  }

  @Override
  public void onReport(double messagesPerSec, double bytesPerSec) {
    for (ThroughputListener listener : listeners) {
      listener.onReport(messagesPerSec, bytesPerSec);
    }
  }
}
