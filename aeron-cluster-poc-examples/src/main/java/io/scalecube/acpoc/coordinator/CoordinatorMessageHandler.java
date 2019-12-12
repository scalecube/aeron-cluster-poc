package io.scalecube.acpoc.coordinator;

import io.scalecube.cluster.transport.api.Message;
import io.scalecube.services.ServiceEndpoint;

public interface CoordinatorMessageHandler {

  void onMessage(CoordinatorContext context, Message message);

  void onEndpointRegistered(CoordinatorContext context, ServiceEndpoint endpoint);

  void onEndpointUnregistered(CoordinatorContext context, ServiceEndpoint endpoint);
}
