package io.scalecube.acpoc.benchmarks;

import io.aeron.CommonContext;
import io.aeron.cluster.client.AeronCluster;
import io.aeron.cluster.client.AeronCluster.AsyncConnect;
import io.aeron.cluster.client.EgressListener;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.MediaDriver.Context;
import io.scalecube.net.Address;
import io.scalecube.services.ServiceEndpoint;
import io.scalecube.services.discovery.ScalecubeServiceDiscovery;
import io.scalecube.services.discovery.api.ServiceDiscovery;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.agrona.CloseHelper;
import org.agrona.concurrent.SleepingIdleStrategy;

public class ClusterClient implements AutoCloseable {

  private MediaDriver clusterClientMediaDriver;
  private AeronCluster clusterClient;

  public static ClusterClient launch(EgressListener egressListener) {
    return new ClusterClient(egressListener);
  }

  private ClusterClient(EgressListener egressListener) {
    try {
      start(egressListener);
    } catch (Throwable th) {
      close();
      throw th;
    }
  }

  private void start(EgressListener egressListener) {
    String clientId = "client-benchmark-" + UUID.randomUUID();
    String clientDirName =
        Paths.get(CommonContext.getAeronDirectoryName(), "aeron", "cluster", clientId).toString();
    System.out.println("Cluster client directory: " + clientDirName);

    clusterClientMediaDriver =
        MediaDriver.launch(
            new Context()
                .aeronDirectoryName(clientDirName)
                .warnIfDirectoryExists(true)
                .dirDeleteOnStart(true)
                .dirDeleteOnShutdown(true)
                .printConfigurationOnStart(true)
                .errorHandler(Throwable::printStackTrace));

    List<Address> endpoints =
        Collections.singletonList(Address.create(Runners.HOST_ADDRESS, Runners.NODE_BASE_PORT));
    if (Runners.CONNECT_VIA_SEED) {
      ServiceDiscovery serviceDiscovery =
          new ScalecubeServiceDiscovery(
                  ServiceEndpoint.builder()
                      .id(UUID.randomUUID().toString())
                      .address(Address.create(Runners.HOST_ADDRESS, 0))
                      .build())
              .options(
                  options -> options.membership(cfg -> cfg.seedMembers(Runners.seedMembers())));
      endpoints = ClusterMember.endpoints(serviceDiscovery).block(Duration.ofMinutes(10));
    }
    String clusterMemberEndpoints = ClusterMember.toClusterMemberEndpoints(endpoints);
    System.out.println(
        LocalDateTime.now() + " [client] clusterMemberEndpoints = " + clusterMemberEndpoints);

    clusterClient =
        connect(
            () ->
                new AeronCluster.Context()
                    .messageTimeoutNs(TimeUnit.SECONDS.toNanos(60))
                    .ingressEndpoints(clusterMemberEndpoints)
                    .ingressChannel(ClusterMember.ingressChannel(Runners.CLIENT_BASE_PORT))
                    .egressChannel(ClusterMember.egressChannel(Runners.CLIENT_BASE_PORT))
                    .egressListener(egressListener)
                    .aeronDirectoryName(clusterClientMediaDriver.aeronDirectoryName())
                    .errorHandler(Throwable::printStackTrace));

    System.out.println(LocalDateTime.now() + " [client] Connected to " + clusterMemberEndpoints);
  }

  public AeronCluster client() {
    return clusterClient;
  }

  @Override
  public void close() {
    CloseHelper.quietClose(clusterClient);
    CloseHelper.quietClose(clusterClientMediaDriver);
  }

  private AeronCluster connect(Supplier<AeronCluster.Context> contextSupplier) {
    AsyncConnect asyncConnect = AeronCluster.asyncConnect(contextSupplier.get().clone());
    SleepingIdleStrategy idleStrategy =
        new SleepingIdleStrategy(TimeUnit.MILLISECONDS.toNanos(200));
    while (true) {
      try {
        AeronCluster aeronCluster = asyncConnect.poll();
        if (aeronCluster != null) {
          return aeronCluster;
        }
      } catch (Throwable th) {
        CloseHelper.quietClose(asyncConnect);
        System.err.println(LocalDateTime.now() + " " + th.getMessage());
        asyncConnect = AeronCluster.asyncConnect(contextSupplier.get().clone());
      }
      System.out.println(LocalDateTime.now() + " waiting to connect");
      idleStrategy.idle();
    }
  }
}
