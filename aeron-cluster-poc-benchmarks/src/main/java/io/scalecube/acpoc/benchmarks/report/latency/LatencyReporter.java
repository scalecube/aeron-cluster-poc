package io.scalecube.acpoc.benchmarks.report.latency;

import io.scalecube.acpoc.benchmarks.Runners;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;
import org.agrona.CloseHelper;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

public class LatencyReporter implements AutoCloseable {

  private final Recorder histogram;
  private final Disposable disposable;

  private Histogram accumulatedHistogram;

  private boolean warmupFinished = false;
  private final LatencyListener listener;

  /**
   * Launch this test reporter.
   *
   * @param listeners throughput listeners
   * @return a reporter
   */
  public static LatencyReporter launch(LatencyListener... listeners) {
    return new LatencyReporter(new CompositeReportingLatencyListener(listeners));
  }

  private LatencyReporter(LatencyListener listener) {
    this.listener = listener;
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

      listener.onReport(intervalHistogram);
    } else {
      warmupFinished = true;
      histogram.reset();
    }
  }

  private void onTerminate() {
    listener.onTerminate(accumulatedHistogram);
  }

  public void onDiff(long diff) {
    histogram.recordValue(diff);
  }

  @Override
  public void close() {
    disposable.dispose();
    histogram.reset();
    CloseHelper.quietClose(listener);
  }
}
