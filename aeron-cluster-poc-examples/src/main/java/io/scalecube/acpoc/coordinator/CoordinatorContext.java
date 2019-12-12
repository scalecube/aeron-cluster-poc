package io.scalecube.acpoc.coordinator;

import io.scalecube.cluster.transport.api.Message;
import io.scalecube.net.Address;
import io.scalecube.services.ServiceEndpoint;
import java.util.List;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

public interface CoordinatorContext {

  Scheduler scheduler();

  List<ServiceEndpoint> serviceEndpoints();

  Mono<Void> send(Address address, Message message);

  Mono<Message> requestResponse(Address address, Message request);
}
