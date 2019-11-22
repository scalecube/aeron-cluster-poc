package io.scalecube.acpoc.benchmarks;

import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;
import java.io.FileWriter;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import org.agrona.CloseHelper;
import reactor.core.Disposable;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/** Tracker and reporter of throughput rates. */
public class RateReporter implements AutoCloseable {

  private final long reportIntervalNs;
  private final ResultReporter reporter;
  private final Disposable disposable;

  private final LongAdder totalBytes = new LongAdder();
  private final LongAdder totalMessages = new LongAdder();

  private long lastTotalBytes;
  private long lastTotalMessages;
  private long lastTimestamp;

  private boolean warmupFinished = false;

  public static RateReporter launch(Class benchmarkClass) {
    return new RateReporter(new CsvResultReporter(benchmarkClass));
  }

  /**
   * Create rate reporter.
   *
   * @param reporter reporter function
   */
  private RateReporter(ResultReporter reporter) {
    Duration reportDelay =
        Duration.ofSeconds(
            Runners.warmupTime().convertTo(TimeUnit.SECONDS) * Runners.warmupIterations());
    Duration reportInterval = Duration.ofSeconds(Long.getLong("benchmark.report.interval", 1));
    this.reportIntervalNs = reportInterval.toNanos();
    this.reporter = reporter;
    this.disposable =
        Flux.interval(reportDelay, reportInterval, Schedulers.single())
            .subscribe(i -> this.run(), Throwable::printStackTrace);
  }

  private void run() {
    long currentTotalMessages = totalMessages.longValue();
    long currentTotalBytes = totalBytes.longValue();
    long currentTimestamp = System.nanoTime();

    long timeSpanNs = currentTimestamp - lastTimestamp;
    double messagesPerSec =
        ((currentTotalMessages - lastTotalMessages) * (double) reportIntervalNs)
            / (double) timeSpanNs;
    double bytesPerSec =
        ((currentTotalBytes - lastTotalBytes) * (double) reportIntervalNs) / (double) timeSpanNs;

    lastTotalBytes = currentTotalBytes;
    lastTotalMessages = currentTotalMessages;
    lastTimestamp = currentTimestamp;

    if (warmupFinished) {
      reporter.onReport(messagesPerSec, bytesPerSec);
    } else {
      warmupFinished = true;
    }
  }

  /**
   * Notify rate reporter of number of messages and bytes received, sent, etc.
   *
   * @param messages received, sent, etc.
   * @param bytes received, sent, etc.
   */
  public void onMessage(final long messages, final long bytes) {
    totalBytes.add(bytes);
    totalMessages.add(messages);
  }

  @Override
  public void close() {
    disposable.dispose();
    CloseHelper.quietClose(reporter);
  }

  /** Interface for reporting of rate information. */
  private interface ResultReporter extends AutoCloseable {
    /**
     * Called for a rate report.
     *
     * @param messagesPerSec since last report
     * @param bytesPerSec since last report
     */
    void onReport(double messagesPerSec, double bytesPerSec);
  }

  private static class CsvResultReporter implements ResultReporter {

    private final ICSVWriter csvWriter;
    private final String[] csvLine;

    private long totalMessages;
    private long totalBytes;
    private long seconds;

    private CsvResultReporter(Class benchmarkClass) {
      try {
        FileWriter fileWriter = new FileWriter(Runners.resultFilename(benchmarkClass), false);
        csvWriter = new CSVWriterBuilder(fileWriter).build();
        String[] title = {"messages/sec", "MB/sec", "total messages", "MB payloads"};
        csvWriter.writeNext(title);
        csvWriter.flushQuietly();
        csvLine = title;
      } catch (Exception e) {
        throw Exceptions.propagate(e);
      }
    }

    @Override
    public void onReport(double messagesPerSec, double bytesPerSec) {
      totalMessages += messagesPerSec;
      totalBytes += bytesPerSec;
      seconds++;
      csvLine[0] = String.format("%.07g", messagesPerSec);
      csvLine[1] = String.format("%.07g", bytesPerSec / (1024 * 1024));
      csvLine[2] = Long.toString(totalMessages);
      csvLine[3] = Long.toString(totalBytes / (1024 * 1024));
      csvWriter.writeNext(csvLine);
      csvWriter.flushQuietly();
      System.out.println(
          csvLine[0]
              + " msgs/sec, "
              + csvLine[1]
              + " MB/sec, totals "
              + csvLine[2]
              + " messages "
              + csvLine[3]
              + " MB payloads");
    }

    @Override
    public void close() {
      csvLine[0] = String.format("%.07g", (double) totalMessages / seconds);
      csvLine[1] = String.format("%.07g", ((double) totalBytes / seconds) / (1024 * 1024));
      csvLine[2] = Long.toString(totalMessages);
      csvLine[3] = Long.toString(totalBytes / (1024 * 1024));
      System.out.println("Throughput average: ");
      System.out.println(
          csvLine[0]
              + " msgs/sec, "
              + csvLine[1]
              + " MB/sec, totals "
              + csvLine[2]
              + " messages "
              + csvLine[3]
              + " MB payloads");
      csvWriter.writeNext(csvLine);
      CloseHelper.quietClose(csvWriter);
    }
  }
}
