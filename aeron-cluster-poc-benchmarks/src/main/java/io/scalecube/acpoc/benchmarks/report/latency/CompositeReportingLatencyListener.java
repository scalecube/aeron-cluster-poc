package io.scalecube.acpoc.benchmarks.report.latency;

import org.HdrHistogram.Histogram;
import org.agrona.CloseHelper;

public class CompositeReportingLatencyListener implements LatencyListener {

  private final LatencyListener[] listeners;

  public CompositeReportingLatencyListener(LatencyListener... listeners) {
    this.listeners = listeners;
  }

  @Override
  public void onReport(Histogram intervalHistogram) {
    for (LatencyListener latencyListener : listeners) {
      latencyListener.onReport(intervalHistogram);
    }
  }

  @Override
  public void close() {
    CloseHelper.quietCloseAll(listeners);
  }

  @Override
  public void onTerminate(Histogram accumulatedHistogram) {
    for (LatencyListener latencyListener : listeners) {
      latencyListener.onTerminate(accumulatedHistogram);
    }
  }
}
