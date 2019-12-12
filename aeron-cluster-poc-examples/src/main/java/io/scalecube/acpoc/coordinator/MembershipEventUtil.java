package io.scalecube.acpoc.coordinator;

import io.scalecube.cluster.membership.MembershipEvent;
import io.scalecube.services.ServiceEndpoint;
import io.scalecube.services.discovery.api.ServiceDiscoveryEvent;

class MembershipEventUtil {

  public static ServiceDiscoveryEvent toServiceDiscoveryEvent(MembershipEvent membershipEvent) {
    ServiceDiscoveryEvent discoveryEvent = null;

    if (membershipEvent.isAdded() && membershipEvent.newMetadata() != null) {
      discoveryEvent =
          ServiceDiscoveryEvent.newEndpointAdded(
              (ServiceEndpoint) MessageCodecImpl.decode(membershipEvent.newMetadata()));
    }

    if (membershipEvent.isRemoved() && membershipEvent.oldMetadata() != null) {
      discoveryEvent =
          ServiceDiscoveryEvent.newEndpointRemoved(
              (ServiceEndpoint) MessageCodecImpl.decode(membershipEvent.oldMetadata()));
    }

    if (membershipEvent.isLeaving() && membershipEvent.newMetadata() != null) {
      discoveryEvent =
          ServiceDiscoveryEvent.newEndpointLeaving(
              (ServiceEndpoint) MessageCodecImpl.decode(membershipEvent.newMetadata()));
    }

    return discoveryEvent;
  }
}
