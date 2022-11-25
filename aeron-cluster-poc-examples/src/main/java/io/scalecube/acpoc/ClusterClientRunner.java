package io.scalecube.acpoc;

import io.aeron.cluster.client.AeronCluster;
import io.aeron.driver.DefaultAllowTerminationValidator;
import io.aeron.driver.MediaDriver;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.agrona.CloseHelper;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Runner to start the cluster client that continuously sends requests to cluster. */
public class ClusterClientRunner {

  public static final Logger logger = LoggerFactory.getLogger(ClusterClientRunner.class);

  /**
   * Main method.
   *
   * @param args program arguments.
   */
  public static void main(String[] args) {
    MediaDriver clientMediaDriver =
        MediaDriver.launch(
            new MediaDriver.Context()
                .errorHandler(ex -> logger.error("Exception occurred at MediaDriver: ", ex))
                .terminationHook(() -> logger.info("TerminationHook called on MediaDriver "))
                .terminationValidator(new DefaultAllowTerminationValidator())
                .warnIfDirectoryExists(true)
                .dirDeleteOnStart(true)
                .dirDeleteOnShutdown(true));

    AeronCluster client =
        AeronCluster.connect(
            new AeronCluster.Context()
                .errorHandler(ex -> logger.error("Exception occurred at AeronCluster: ", ex))
                .egressListener(new EgressListenerImpl())
                .aeronDirectoryName(clientMediaDriver.aeronDirectoryName())
                .ingressChannel("aeron:udp"));

    Disposable sender =
        Flux.interval(Duration.ofMillis(500))
            .subscribe(
                i -> {
                  String request = "Hello to cluster " + i;

                  byte[] bytes = request.getBytes(StandardCharsets.UTF_8);
                  UnsafeBuffer buffer = new UnsafeBuffer(bytes);
                  long l = client.offer(buffer, 0, bytes.length);

                  logger.info("Client: REQUEST {} send, result={}", i, l);

                  if (true) {
                    try {
                      if (!client.sendKeepAlive()) {
                        System.err.println("sendKeepAlive failed");
                      }
                      System.err.println("client.leaderMemberId = " + client.leaderMemberId());
                      System.err.println("client.clusterSessionId = " + client.clusterSessionId());
                      System.err.println("client.leadershipTermId = " + client.leadershipTermId());

                      if (client.isClosed()) {
                        System.err.println("client.isClosed");
                      }
                      if (!client.egressSubscription().isConnected()) {
                        System.err.println("egressSubscription is not connected");
                      }
                      if (!client.ingressPublication().isConnected()) {
                        System.err.println("ingressPublication is not connected");
                      }
                      System.err.println("-----------------------------------------------------");
                    } catch (Exception e) {
                      e.printStackTrace();
                    }
                  }
                });

    Disposable receiver =
        Flux.interval(Duration.ofMillis(100)) //
            .subscribe(i -> client.pollEgress());

    Mono<Void> onShutdown =
        Utils.onShutdown(
            () -> {
              sender.dispose();
              receiver.dispose();
              CloseHelper.close(client);
              CloseHelper.close(clientMediaDriver);
              return null;
            });
    onShutdown.block();
  }
}
