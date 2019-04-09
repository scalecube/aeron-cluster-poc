package io.scalecube.acpoc;

import java.time.Duration;

public class Configurations {

  public static final Duration SNAPSHOT_PERIOD =
      Duration.ofSeconds(Integer.getInteger("io.scalecube.acpoc.snapshotPeriodSecs", 20));

  private Configurations() {
    // no-op
  }
}
