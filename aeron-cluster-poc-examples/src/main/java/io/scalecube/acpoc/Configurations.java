package io.scalecube.acpoc;

import java.time.Duration;

public class Configurations {

  public static final Duration SNAPSHOT_PERIOD =
      Duration.ofSeconds(Integer.getInteger("io.scalecube.acpoc.snapshotPeriodSecs", 20));

  public static final long MAX_CATALOG_ENTRIES = 1024;

  public static final String INSTANCE_ID =
      System.getProperty("io.scalecube.acpoc.instanceId", null);

  public static final boolean CLEAN_START = Boolean.getBoolean("io.scalecube.acpoc.cleanStart");
  public static final boolean CLEAN_SHUTDOWN =
      Boolean.getBoolean("io.scalecube.acpoc.cleanShutdown");

  // --------------------------------------------------
  // -- benchmark settings ----------------------------
  // --------------------------------------------------

  public static final int MESSAGE_LENGTH =
      Integer.getInteger("io.scalecube.acpoc.messageLength", 32);
  public static final long REPORT_INTERVAL = Long.getLong("io.scalecube.acpoc.report.interval", 1);
  public static final long WARMUP_REPORT_DELAY =
      Long.getLong("io.scalecube.acpoc.report.delay", REPORT_INTERVAL);
  public static final long TRACE_REPORTER_INTERVAL =
      Long.getLong("io.scalecube.acpoc.trace.report.interval", 60);
  public static final String TARGET_FOLDER_FOLDER_LATENCY =
      System.getProperty(
          "io.scalecube.acpoc.report.traces.folder.latency", "./target/traces/reports/latency/");
  public static final String REPORT_NAME =
      System.getProperty("io.scalecube.acpoc.report.name", String.valueOf(System.nanoTime()));
  public static final long NUMBER_OF_MESSAGES =
      Long.getLong("io.scalecube.acpoc.messages", 100_000_000);
  public static final int REQUESTED = Integer.getInteger("io.scalecube.acpoc.request", 16);

  private Configurations() {
    // no-op
  }
}
