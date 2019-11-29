package io.scalecube.acpoc.benchmarks.report.latency;

import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;
import io.scalecube.acpoc.benchmarks.Runners;
import java.io.FileWriter;
import org.HdrHistogram.Histogram;
import org.agrona.CloseHelper;
import reactor.core.Exceptions;

public class CsvReportingLatencyListener implements LatencyListener {

  private static final double VALUE_UNIT_SCALING_RATIO = 1000.0; // microseconds

  private final ICSVWriter csvWriter;
  private final String[] csvLine;

  /**
   * Initialize CSV latency listener.
   *
   * @param benchmarkClass benchmark class
   */
  public CsvReportingLatencyListener(Class<?> benchmarkClass) {
    try {
      FileWriter fileWriter = new FileWriter(Runners.resultFilename(benchmarkClass), false);
      csvWriter = new CSVWriterBuilder(fileWriter).build();
      String[] title = {"p70", "p80", "p90", "p99"};
      csvWriter.writeNext(title);
      csvWriter.flushQuietly();
      csvLine = title;
    } catch (Exception e) {
      throw Exceptions.propagate(e);
    }
  }

  @Override
  public void onReport(Histogram histogram) {
    csvLine[0] =
        String.format("%.03g", histogram.getValueAtPercentile(70d) / VALUE_UNIT_SCALING_RATIO);
    csvLine[1] =
        String.format("%.03g", histogram.getValueAtPercentile(80d) / VALUE_UNIT_SCALING_RATIO);
    csvLine[2] =
        String.format("%.03g", histogram.getValueAtPercentile(90d) / VALUE_UNIT_SCALING_RATIO);
    csvLine[3] =
        String.format("%.03g", histogram.getValueAtPercentile(99d) / VALUE_UNIT_SCALING_RATIO);

    csvWriter.writeNext(csvLine);
    csvWriter.flushQuietly();
  }

  @Override
  public void close() {
    CloseHelper.quietClose(csvWriter);
  }

  @Override
  public void onTerminate(Histogram accumulatedHistogram) {
    // nothing to do here
  }
}
