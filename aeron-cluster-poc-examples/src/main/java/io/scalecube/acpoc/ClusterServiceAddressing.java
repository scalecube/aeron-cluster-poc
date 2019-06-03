package io.scalecube.acpoc;

import static io.scalecube.acpoc.AeronClusterConstants.ARCHIVE_CONTROL_REQUEST_CHANNEL_PORT_OFFSET;
import static io.scalecube.acpoc.AeronClusterConstants.ARCHIVE_CONTROL_REQUEST_STREAM_ID_OFFSET;
import static io.scalecube.acpoc.AeronClusterConstants.ARCHIVE_CONTROL_RESPONSE_CHANNEL_PORT_OFFSET;
import static io.scalecube.acpoc.AeronClusterConstants.ARCHIVE_CONTROL_RESPONSE_STREAM_ID_OFFSET;
import static io.scalecube.acpoc.AeronClusterConstants.ARCHIVE_RECORDING_EVENTS_CHANNEL_PORT_OFFSET;
import static io.scalecube.acpoc.AeronClusterConstants.LOG_CHANNEL_PORT_OFFSET;
import static io.scalecube.acpoc.AeronClusterConstants.ORDER_EVENTS_CHANNEL_PORT_OFFSET;
import static io.scalecube.acpoc.AeronClusterConstants.ORDER_EVENTS_CHANNEL_STREAM_ID_OFFSET;
import static io.scalecube.acpoc.AeronClusterConstants.SNAPSHOT_CHANNEL_PORT_OFFSET;
import static io.scalecube.acpoc.AeronClusterConstants.SNAPSHOT_CHANNEL_STREAM_ID_OFFSET;

import io.aeron.ChannelUriStringBuilder;
import io.aeron.CommonContext;
import io.aeron.cluster.ConsensusModule.Configuration;
import io.aeron.cluster.ConsensusModule.Context;
import java.util.Collection;
import java.util.stream.Collectors;

public class ClusterServiceAddressing {

  final Address address;

  public ClusterServiceAddressing(Address address) {
    this.address = address;
  }

  public Address logChannelAddress() {
    return Address.create(address.host(), address.port() + LOG_CHANNEL_PORT_OFFSET);
  }

  public Address archiveControlRequestChannelAddress() {
    return Address.create(
        address.host(), address.port() + ARCHIVE_CONTROL_REQUEST_CHANNEL_PORT_OFFSET);
  }

  public Address archiveControlResponseChannelAddress() {
    return Address.create(
        address.host(), address.port() + ARCHIVE_CONTROL_RESPONSE_CHANNEL_PORT_OFFSET);
  }

  public Address archiveRecordingEventsChannelAddress() {
    return Address.create(
        address.host(), address.port() + ARCHIVE_RECORDING_EVENTS_CHANNEL_PORT_OFFSET);
  }

  public Address snapshotChannelAddress() {
    return Address.create(address.host(), address.port() + SNAPSHOT_CHANNEL_PORT_OFFSET);
  }

  public Address orderEventsChannelAddress() {
    return Address.create(address.host(), address.port() + ORDER_EVENTS_CHANNEL_PORT_OFFSET);
  }

  public int archiveControlRequestStreamId() {
    return address.port() + ARCHIVE_CONTROL_REQUEST_STREAM_ID_OFFSET;
  }

  public int archiveControlResponseStreamId() {
    return address.port() + ARCHIVE_CONTROL_RESPONSE_STREAM_ID_OFFSET;
  }

  public int snapshotStreamId() {
    return address.port() + SNAPSHOT_CHANNEL_STREAM_ID_OFFSET;
  }

  public int orderEventsStreamId() {
    return address.port() + ORDER_EVENTS_CHANNEL_STREAM_ID_OFFSET;
  }

  /**
   * Returns log channel.
   *
   * @return log channel
   */
  public String logChannel() {
    return new ChannelUriStringBuilder()
        .media("udp")
        .reliable(true)
        .controlMode("manual")
        .controlEndpoint(logChannelAddress().toString())
        .build();
  }

  /**
   * Returns archive control request channel.
   *
   * @return archive control request channel
   */
  public String archiveControlRequestChannel() {
    return new ChannelUriStringBuilder()
        .media("udp")
        .reliable(true)
        .endpoint(archiveControlRequestChannelAddress().toString())
        .build();
  }

  /**
   * Returns archive control response channel.
   *
   * @return archive control response channel
   */
  public String archiveControlResponseChannel() {
    return new ChannelUriStringBuilder()
        .media("udp")
        .reliable(true)
        .endpoint(archiveControlResponseChannelAddress().toString())
        .build();
  }

  /**
   * Returns archive recording events channel.
   *
   * @return archive recording events channel
   */
  public String archiveRecordingEventsChannel() {
    return new ChannelUriStringBuilder()
        .media("udp")
        .reliable(true)
        .controlMode("dynamic")
        .controlEndpoint(archiveRecordingEventsChannelAddress().toString())
        .build();
  }

  /**
   * Returns snapshot channel.
   *
   * @return snapshot channel
   */
  public String snapshotChannel() {
    return new ChannelUriStringBuilder()
        .media(CommonContext.UDP_MEDIA)
        .reliable(true)
        .endpoint(snapshotChannelAddress().toString())
        .build();
  }

  /**
   * Returns order events channel.
   *
   * @return order events channel
   */
  public String orderEventsChannel() {
    return new ChannelUriStringBuilder()
        .media(CommonContext.UDP_MEDIA)
        .reliable(true)
        .endpoint(orderEventsChannelAddress().toString())
        .build();
  }

  /**
   * Converts to a string suitable for setting {@link Context#clusterMembers(String)}. <b>NOTE</b>:
   * this setting is for static cluster.
   */
  public static String toClusterMembers(Collection<Address> clusterMembers) {
    return clusterMembers.stream()
        .map(ClusterServiceAddressing::new)
        .map(ClusterServiceAddressing::asString)
        .collect(Collectors.joining("|"));
  }

  /**
   * Utility function to form an aeron cluster compliant cluster members string. See for details
   * {@link Configuration#CLUSTER_MEMBERS_PROP_NAME}
   *
   * @return cluster members string in aeron cluster format.
   */
  private String asString() {
    return new StringBuilder()
        .append(address.hashCode())
        .append(',')
        .append(address.host())
        .append(':')
        .append(address.port() + AeronClusterConstants.SERVICE_CLIENT_FACING_PORT_OFFSET)
        .append(',')
        .append(address.host())
        .append(':')
        .append(address.port() + AeronClusterConstants.MEMBER_FACTING_PORT_OFFSET)
        .append(',')
        .append(address.host())
        .append(':')
        .append(address.port() + AeronClusterConstants.LOG_PORT_OFFSET)
        .append(',')
        .append(address.host())
        .append(':')
        .append(address.port() + AeronClusterConstants.TRANSFER_PORT_OFFSET)
        .append(',')
        .append(address.host())
        .append(':')
        .append(address.port() + ARCHIVE_CONTROL_REQUEST_CHANNEL_PORT_OFFSET)
        .toString();
  }

  @Override
  public String toString() {
    return "ClusterServiceAddressing{" + asString() + "}";
  }
}
