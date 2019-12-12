package io.scalecube.acpoc.coordinator;

import io.scalecube.cluster.Cluster;
import io.scalecube.cluster.transport.api.Message;
import io.scalecube.net.Address;
import io.scalecube.services.ServiceEndpoint;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

class CoordinatorContextImpl implements CoordinatorContext {

  private final Scheduler scheduler;
  private final Cluster cluster;
  private final List<ServiceEndpoint> serviceEndpoints;

  public CoordinatorContextImpl(
      Scheduler scheduler, Cluster cluster, Collection<ServiceEndpoint> serviceEndpoints) {
    this.scheduler = scheduler;
    this.cluster = cluster;
    this.serviceEndpoints = Collections.unmodifiableList(new ArrayList<>(serviceEndpoints));
  }

  @Override
  public Scheduler scheduler() {
    return scheduler;
  }

  @Override
  public List<ServiceEndpoint> serviceEndpoints() {
    return serviceEndpoints;
  }

  @Override
  public Mono<Void> send(Address address, Message message) {
    return cluster.send(address, message);
  }

  @Override
  public Mono<Message> requestResponse(Address address, Message request) {
    return cluster.requestResponse(address, request);
  }
}
