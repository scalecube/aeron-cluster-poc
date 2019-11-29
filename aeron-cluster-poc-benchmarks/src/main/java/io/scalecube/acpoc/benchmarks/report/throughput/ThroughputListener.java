package io.scalecube.acpoc.benchmarks.report.throughput;

/** Interface for reporting of rate information. */
public interface ThroughputListener extends AutoCloseable {
  /**
   * Called for a rate report.
   *
   * @param messagesPerSec since last report
   * @param bytesPerSec since last report
   */
  void onReport(double messagesPerSec, double bytesPerSec);
}
