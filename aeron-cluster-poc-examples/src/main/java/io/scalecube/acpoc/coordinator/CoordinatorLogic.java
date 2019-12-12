package io.scalecube.acpoc.coordinator;

import io.scalecube.cluster.transport.api.Message;
import io.scalecube.services.ServiceEndpoint;

public interface CoordinatorLogic {

  default void onDutyCycle(CoordinatorContext context) {
    // no-op
  }

  default void onMessage(CoordinatorContext context, Message message) {
    // no-op
  }

  default void onEndpointRegistered(CoordinatorContext context, ServiceEndpoint endpoint) {
    // no-op
  }

  default void onEndpointUnregistered(CoordinatorContext context, ServiceEndpoint endpoint) {
    // no-op
  }
}
