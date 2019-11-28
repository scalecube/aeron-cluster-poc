package io.scalecube.acpoc.benchmarks.cluster;

public class ClusterConstants {

  public static final int LOG_CHANNEL_PORT_OFFSET = 1;
  public static final int ARCHIVE_CONTROL_REQUEST_CHANNEL_PORT_OFFSET = 2;
  public static final int ARCHIVE_CONTROL_RESPONSE_CHANNEL_PORT_OFFSET = 3;
  public static final int ARCHIVE_RECORDING_EVENTS_CHANNEL_PORT_OFFSET = 4;
  public static final int SERVICE_CLIENT_FACING_PORT_OFFSET = 5;
  public static final int MEMBER_FACTING_PORT_OFFSET = 6;
  public static final int LOG_PORT_OFFSET = 7;
  public static final int TRANSFER_PORT_OFFSET = 8;
  public static final int INGRESS_CHANNEL_PORT_OFFSET = 9;
  public static final int EGRESS_CHANNEL_PORT_OFFSET = 10;
  public static final int SNAPSHOT_CHANNEL_PORT_OFFSET = 11;
  public static final int ORDER_EVENTS_CHANNEL_PORT_OFFSET = 12;

  public static final int ARCHIVE_CONTROL_REQUEST_STREAM_ID_OFFSET = 100;
  public static final int ARCHIVE_CONTROL_RESPONSE_STREAM_ID_OFFSET = 110;

  public static final int SNAPSHOT_STREAM_ID = 120;
  public static final int ORDER_EVENTS_STREAM_ID = 130;

  private ClusterConstants() {
    // Do not instantiate
  }
}
