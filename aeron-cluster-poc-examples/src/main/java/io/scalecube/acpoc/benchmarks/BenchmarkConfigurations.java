package io.scalecube.acpoc.benchmarks;

public interface BenchmarkConfigurations {

  int MESSAGE_LENGTH = Integer.getInteger("io.scalecube.acpoc.messageLength", 32);
  long REPORT_INTERVAL = Long.getLong("io.scalecube.acpoc.report.interval", 1);
  long WARMUP_REPORT_DELAY = Long.getLong("io.scalecube.acpoc.report.delay", REPORT_INTERVAL);
  long TRACE_REPORTER_INTERVAL = Long.getLong("io.scalecube.acpoc.trace.report.interval", 60);
  String TARGET_FOLDER_FOLDER_LATENCY =
      System.getProperty(
          "io.scalecube.acpoc.report.traces.folder.latency", "./target/traces/reports/latency/");
  String REPORT_NAME =
      System.getProperty("io.scalecube.acpoc.report.name", String.valueOf(System.nanoTime()));
  long NUMBER_OF_MESSAGES = Long.getLong("io.scalecube.acpoc.messages", 100_000_000);
  int REQUESTED = Integer.getInteger("io.scalecube.acpoc.request", 16);
}
