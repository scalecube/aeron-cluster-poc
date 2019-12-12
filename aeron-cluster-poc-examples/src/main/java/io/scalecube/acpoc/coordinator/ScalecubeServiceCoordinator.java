package io.scalecube.acpoc.coordinator;

import io.scalecube.cluster.Cluster;
import io.scalecube.cluster.ClusterConfig;
import io.scalecube.cluster.ClusterImpl;
import io.scalecube.cluster.ClusterMessageHandler;
import io.scalecube.cluster.membership.MembershipEvent;
import io.scalecube.cluster.transport.api.Message;
import io.scalecube.services.ServiceEndpoint;
import io.scalecube.services.discovery.api.ServiceDiscoveryEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.DirectProcessor;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

public class ScalecubeServiceCoordinator {

  private static final Logger LOGGER = LoggerFactory.getLogger(ScalecubeServiceCoordinator.class);

  private ServiceEndpoint serviceEndpoint;
  private ClusterConfig clusterConfig;
  private Cluster cluster;

  private Scheduler scheduler;

  private final Map<String, ServiceEndpoint> serviceEndpoints = new HashMap<>();

  private final DirectProcessor<ServiceDiscoveryEvent> subject = DirectProcessor.create();
  private final FluxSink<ServiceDiscoveryEvent> sink = subject.sink();

  public ScalecubeServiceCoordinator() {
    clusterConfig =
        ClusterConfig.defaultLanConfig()
            .transport(config -> config.messageCodec(new MessageCodecImpl()))
            .metadataEncoder(MessageCodecImpl::encode)
            .metadataDecoder(MessageCodecImpl::decode);
  }

  /**
   * Copy constructor.
   *
   * @param other other instance
   */
  private ScalecubeServiceCoordinator(ScalecubeServiceCoordinator other) {
    this.serviceEndpoint = other.serviceEndpoint;
    this.clusterConfig = other.clusterConfig;
    this.cluster = other.cluster;
    this.scheduler = other.scheduler;
  }

  /**
   * Setter for {@code ClusterConfig.Builder} options.
   *
   * @param opts ClusterConfig options builder
   * @return new instance of {@code ScalecubeServiceCoordinator}
   */
  public ScalecubeServiceCoordinator config(UnaryOperator<ClusterConfig> opts) {
    ScalecubeServiceCoordinator d = new ScalecubeServiceCoordinator(this);
    d.clusterConfig = opts.apply(clusterConfig);
    return d;
  }

  /**
   * Setter for {@code serviceEndpoint} option.
   *
   * @param endpointSupplier endpointSupplier builder
   * @return new instance of {@code ScalecubeServiceCoordinator}
   */
  public ScalecubeServiceCoordinator endpoint(Supplier<ServiceEndpoint> endpointSupplier) {
    ScalecubeServiceCoordinator d = new ScalecubeServiceCoordinator(this);
    d.serviceEndpoint = endpointSupplier.get();
    d.clusterConfig = clusterConfig.metadata(d.serviceEndpoint);
    return d;
  }

  public Mono<ScalecubeServiceCoordinator> start() {
    return Mono.defer(
        () -> {
          // Start scalecube-cluster and listen membership events
          return new ClusterImpl()
              .config(options -> clusterConfig)
              .handler(
                  cluster -> {
                    return new ClusterMessageHandler() {
                      @Override
                      public void onMembershipEvent(MembershipEvent event) {
                        scheduler.schedule(
                            () -> ScalecubeServiceCoordinator.this.onMembershipEvent(event));
                      }

                      @Override
                      public void onMessage(Message message) {
                        scheduler.schedule(
                            () -> ScalecubeServiceCoordinator.this.onMessage(message));
                      }
                    };
                  })
              .start()
              .doOnSuccess(
                  cluster -> {
                    this.cluster = cluster;
                    this.scheduler = Schedulers.newSingle("scalecube-coordinator");
                  })
              .thenReturn(this);
        });
  }

  public Mono<Void> shutdown() {
    return Mono.defer(
        () -> {
          if (cluster == null) {
            sink.complete();
            return Mono.empty();
          }
          cluster.shutdown();
          return cluster
              .onShutdown()
              .doFinally(s -> sink.complete())
              .doFinally(s -> scheduler.dispose());
        });
  }

  private void onMembershipEvent(MembershipEvent membershipEvent) {
    LOGGER.debug("onMembershipEvent: {}", membershipEvent);

    ServiceDiscoveryEvent discoveryEvent =
        MembershipEventUtil.toServiceDiscoveryEvent(membershipEvent);
    if (discoveryEvent == null) {
      LOGGER.warn(
          "Not publishing discoveryEvent, discoveryEvent is null, membershipEvent: {}",
          membershipEvent);
      return;
    }

    if (discoveryEvent.isEndpointAdded()) {
      boolean success = serviceEndpoints.putIfAbsent(serviceEndpoint.id(), serviceEndpoint) == null;
      if (success) {
        LOGGER.info("ServiceEndpoint registered: {}", serviceEndpoint);
      }
    }

    if (discoveryEvent.isEndpointRemoved()) {
      String endpointId = discoveryEvent.serviceEndpoint().id();
      ServiceEndpoint serviceEndpoint = serviceEndpoints.remove(endpointId);
      if (serviceEndpoint != null) {
        LOGGER.info("ServiceEndpoint unregistered: {}", serviceEndpoint);
      }
    }
  }

  private void onMessage(Message message) {
    LOGGER.debug("onMessage: {}", message);
  }
}
