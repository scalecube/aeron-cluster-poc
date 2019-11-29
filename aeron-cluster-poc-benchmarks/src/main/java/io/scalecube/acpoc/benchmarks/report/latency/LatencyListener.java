package io.scalecube.acpoc.benchmarks.report.latency;

import org.HdrHistogram.Histogram;

public interface LatencyListener extends AutoCloseable {
  /**
   * Called for a latency report.
   *
   * @param intervalHistogram the histogram.
   */
  void onReport(Histogram intervalHistogram);

  void onTerminate(Histogram accumulatedHistogram);
}
