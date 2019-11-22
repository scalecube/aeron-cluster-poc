package io.scalecube.acpoc.benchmarks;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

public class LatencyReporter implements AutoCloseable {

  private final Recorder histogram;
  private final Disposable disposable;

  private Histogram accumulatedHistogram;
  private boolean warmupFinished = false;

  public static LatencyReporter launch(Class benchmarkClass) {
    return new LatencyReporter();
  }

  /** Create latency reporter. */
  private LatencyReporter() {
    this.histogram = new Recorder(TimeUnit.SECONDS.toNanos(10), 3);
    Duration reportDelay =
        Duration.ofSeconds(
            Runners.warmupTime().convertTo(TimeUnit.SECONDS) * Runners.warmupIterations());
    Duration reportInterval = Duration.ofSeconds(Long.getLong("benchmark.report.interval", 1));
    this.disposable =
        Flux.interval(reportDelay, reportInterval, Schedulers.single())
            .doOnCancel(this::onTerminate)
            .subscribe(i -> this.run(), Throwable::printStackTrace);
  }

  private void run() {
    if (warmupFinished) {
      Histogram intervalHistogram = histogram.getIntervalHistogram();
      if (accumulatedHistogram != null) {
        accumulatedHistogram.add(intervalHistogram);
      } else {
        accumulatedHistogram = intervalHistogram;
      }
      System.err.println("---- INTERVAL HISTOGRAM ----");
      intervalHistogram.outputPercentileDistribution(System.err, 5, 1000.0, false);
      System.err.println("---- INTERVAL HISTOGRAM ----");
    } else {
      warmupFinished = true;
      histogram.reset();
    }
  }

  private void onTerminate() {
    System.err.println("---- ACCUMULATED HISTOGRAM ----");
    accumulatedHistogram.outputPercentileDistribution(System.err, 5, 1000.0, false);
    System.err.println("---- ACCUMULATED HISTOGRAM ----");
  }

  public void onDiff(long diff) {
    histogram.recordValue(diff);
  }

  @Override
  public void close() {
    disposable.dispose();
    histogram.reset();
  }
}
