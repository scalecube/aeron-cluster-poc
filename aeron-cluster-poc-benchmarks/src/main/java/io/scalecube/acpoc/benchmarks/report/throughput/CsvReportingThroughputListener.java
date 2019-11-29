package io.scalecube.acpoc.benchmarks.report.throughput;

import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;
import io.scalecube.acpoc.benchmarks.Runners;
import java.io.FileWriter;
import org.agrona.CloseHelper;
import reactor.core.Exceptions;

public class CsvReportingThroughputListener implements ThroughputListener {

  private final ICSVWriter csvWriter;
  private final String[] csvLine;

  private long totalMessages;
  private long totalBytes;
  private long seconds;

  /**
   * Initialize CSV throughput listener.
   *
   * @param benchmarkClass benchmark class
   */
  public CsvReportingThroughputListener(Class<?> benchmarkClass) {
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
