package io.scalecube.acpoc.benchmarks;

import io.aeron.ChannelUriStringBuilder;
import io.aeron.cluster.ConsensusModule.Configuration;
import io.aeron.cluster.ConsensusModule.Context;
import io.scalecube.net.Address;
import java.util.Collection;
import java.util.stream.Collectors;

/** Class-aggregator of addressing functions. */
public class ClusteredServiceAddressing implements Cloneable {

  private final Address address;

  public ClusteredServiceAddressing(Address address) {
    this.address = address;
  }

  public Address logChannelAddress() {
    return Address.create(
        address.host(), address.port() + ClusterConstants.LOG_CHANNEL_PORT_OFFSET);
  }

  /**
   * get the Archive Control Request Channel Address.
   *
   * @return the Archive Control Request Channel Address
   */
  public Address archiveControlRequestChannelAddress() {
    return Address.create(
        address.host(),
        address.port() + ClusterConstants.ARCHIVE_CONTROL_REQUEST_CHANNEL_PORT_OFFSET);
  }

  /**
   * get the Archive Control Response Channel Address.
   *
   * @return the Archive Control Response Channel Address
   */
  public Address archiveControlResponseChannelAddress() {
    return Address.create(
        address.host(),
        address.port() + ClusterConstants.ARCHIVE_CONTROL_RESPONSE_CHANNEL_PORT_OFFSET);
  }

  /**
   * get the Archive Recording Events Channel Address.
   *
   * @return the Archive Recording Events Channel Address.
   */
  public Address archiveRecordingEventsChannelAddress() {
    return Address.create(
        address.host(),
        address.port() + ClusterConstants.ARCHIVE_RECORDING_EVENTS_CHANNEL_PORT_OFFSET);
  }

  public int archiveControlRequestStreamId() {
    return address.port() + ClusterConstants.ARCHIVE_CONTROL_REQUEST_STREAM_ID_OFFSET;
  }

  public int archiveControlResponseStreamId() {
    return address.port() + ClusterConstants.ARCHIVE_CONTROL_RESPONSE_STREAM_ID_OFFSET;
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
   * Converts to a string suitable for setting {@link Context#clusterMembers(String)}. <b>NOTE</b>:
   * this setting is for static cluster.
   */
  public static String toClusterMembers(Collection<Address> clusterMembers) {
    return clusterMembers.stream()
        .map(ClusteredServiceAddressing::new)
        .map(ClusteredServiceAddressing::asString)
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
        .append(address.port() + ClusterConstants.SERVICE_CLIENT_FACING_PORT_OFFSET)
        .append(',')
        .append(address.host())
        .append(':')
        .append(address.port() + ClusterConstants.MEMBER_FACTING_PORT_OFFSET)
        .append(',')
        .append(address.host())
        .append(':')
        .append(address.port() + ClusterConstants.LOG_PORT_OFFSET)
        .append(',')
        .append(address.host())
        .append(':')
        .append(address.port() + ClusterConstants.TRANSFER_PORT_OFFSET)
        .append(',')
        .append(address.host())
        .append(':')
        .append(address.port() + ClusterConstants.ARCHIVE_CONTROL_REQUEST_CHANNEL_PORT_OFFSET)
        .toString();
  }
}
