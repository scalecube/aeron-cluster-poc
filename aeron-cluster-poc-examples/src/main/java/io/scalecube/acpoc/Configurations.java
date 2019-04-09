package io.scalecube.acpoc;

import java.time.Duration;

public class Configurations {

  public static final Duration SNAPSHOT_PERIOD =
      Duration.ofSeconds(Integer.getInteger("io.scalecube.acpoc.snapshotPeriodSecs", 20));

  public static final long MAX_CATALOG_ENTRIES = 1024;

  public static final String INSTANCE_ID =
      System.getProperty("io.scalecube.acpoc.instanceId", null);

  public static final boolean CLEAN_START = Boolean.getBoolean("io.scalecube.acpoc.cleanStart");

  private Configurations() {
    // no-op
  }
}
