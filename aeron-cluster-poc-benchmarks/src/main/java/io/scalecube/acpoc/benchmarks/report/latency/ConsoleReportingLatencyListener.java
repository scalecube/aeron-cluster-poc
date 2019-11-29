package io.scalecube.acpoc.benchmarks.report.latency;

import org.HdrHistogram.Histogram;

public class ConsoleReportingLatencyListener implements LatencyListener {

  @Override
  public void onReport(Histogram histogram) {
    histogram.outputPercentileDistribution(System.err, 5, 1000.0, false);
  }

  @Override
  public void close() throws Exception {
    System.err.println("done"); 
  }

  @Override
  public void onTerminate(Histogram accumulatedHistogram) {
    accumulatedHistogram.outputPercentileDistribution(System.err, 5, 1000.0, false);
  }
}
