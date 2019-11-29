package io.scalecube.acpoc;

public class Configurations {

  public static final long MAX_CATALOG_ENTRIES = 1024;

  public static final String INSTANCE_ID =
      System.getProperty("io.scalecube.acpoc.instanceId", null);

  public static final boolean CLEAN_START = Boolean.getBoolean("io.scalecube.acpoc.cleanStart");
  public static final boolean CLEAN_SHUTDOWN =
      Boolean.getBoolean("io.scalecube.acpoc.cleanShutdown");

  private Configurations() {
    // no-op
  }
}
