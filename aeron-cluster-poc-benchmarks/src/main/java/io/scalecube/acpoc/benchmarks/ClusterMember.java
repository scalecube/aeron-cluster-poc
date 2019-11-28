package io.scalecube.acpoc.benchmarks;

import io.aeron.ChannelUriStringBuilder;
import io.scalecube.net.Address;
import io.scalecube.services.ServiceEndpoint;
import io.scalecube.services.discovery.api.ServiceDiscovery;
import io.scalecube.services.discovery.api.ServiceDiscoveryEvent;
import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;
import reactor.core.publisher.Mono;

public class ClusterMember {

  /**
   * Connects to the cluster with a given seeds to get cluster member endpoints.
   *
   * @return clusterMemberEndpoints
   */
  public static Mono<List<Address>> endpoints(ServiceDiscovery serviceDiscovery) {
    return serviceDiscovery
        .listenDiscovery()
        .mergeWith(
            serviceDiscovery
                .start()
                .doOnNext(
                    self -> System.out.println("service discovery listen on " + self.address()))
                .then(Mono.empty()))
        .doOnNext(System.out::println)
        .filter(ServiceDiscoveryEvent::isGroupAdded)
        .take(1)
        .flatMapIterable(ServiceDiscoveryEvent::serviceEndpoints)
        .map(ServiceEndpoint::address)
        .collectList()
        .doOnNext(endpoints -> System.out.println("group endpoints = " + endpoints))
        .flatMap(endpoints -> serviceDiscovery.shutdown().thenReturn(endpoints));
  }

  /**
   * Converts a given addresses to cluster member endpoints property.
   *
   * @param endpoints endpoints
   * @return cluster member endpoints
   */
  public static String toClusterMemberEndpoints(Collection<Address> endpoints) {
    StringJoiner joiner = new StringJoiner(",");
    endpoints.forEach(
        address ->
            joiner.add(
                new StringBuilder()
                    .append(address.hashCode())
                    .append('=')
                    .append(address.host())
                    .append(':')
                    .append(address.port() + ClusterConstants.SERVICE_CLIENT_FACING_PORT_OFFSET)));

    return joiner.toString();
  }

  public static String egressChannel() {
    return egressChannel(Runners.CLIENT_BASE_PORT);
  }

  /**
   * Returns egress channel by a given base port.
   *
   * @param basePort base port
   * @return egress channel
   */
  public static String egressChannel(int basePort) {
    return new ChannelUriStringBuilder()
        .media("udp")
        .reliable(true)
        .endpoint(
            Address.create(
                    Runners.HOST_ADDRESS, basePort + ClusterConstants.EGRESS_CHANNEL_PORT_OFFSET)
                .toString())
        .build();
  }

  public static String ingressChannel() {
    return ingressChannel(Runners.CLIENT_BASE_PORT);
  }

  /**
   * Returns ingress channel by a given base port.
   *
   * @param basePort base port
   * @return ingress channel
   */
  public static String ingressChannel(int basePort) {
    return new ChannelUriStringBuilder()
        .media("udp")
        .reliable(true)
        .endpoint(
            Address.create(
                    Runners.HOST_ADDRESS, basePort + ClusterConstants.INGRESS_CHANNEL_PORT_OFFSET)
                .toString())
        .build();
  }
}
