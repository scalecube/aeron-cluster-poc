package io.scalecube.acpoc;

import io.aeron.cluster.client.AeronCluster;
import io.aeron.driver.DefaultAllowTerminationValidator;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Duration;
import org.agrona.BitUtil;
import org.agrona.BufferUtil;
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
  private static final int MESSAGE_LENGTH = Integer.getInteger("message.length", 1024);

  /**
   * Main method.
   *
   * @param args program arguments.
   */
  public static void main(String[] args) {
    String clientId = "client-" + Utils.instanceId();
    String clientDirName = Paths.get("target", "aeron", "cluster", clientId).toString();

    System.out.println("Cluster client directory: " + clientDirName);

    MediaDriver clientMediaDriver =
        MediaDriver.launch(
            new MediaDriver.Context()
                .errorHandler(ex -> logger.error("Exception occurred at MediaDriver: ", ex))
                .terminationHook(() -> logger.info("TerminationHook called on MediaDriver "))
                .terminationValidator(new DefaultAllowTerminationValidator())
                .threadingMode(ThreadingMode.SHARED)
                .warnIfDirectoryExists(true)
                .dirDeleteOnStart(true)
                .aeronDirectoryName(clientDirName));

    AeronCluster client =
        AeronCluster.connect(
            new AeronCluster.Context()
                .errorHandler(ex -> logger.error("Exception occurred at AeronCluster: ", ex))
                .egressListener(new EgressListenerImpl())
                .aeronDirectoryName(clientDirName)
                .ingressChannel("aeron:udp"));

    UnsafeBuffer buffer =
        new UnsafeBuffer(
            BufferUtil.allocateDirectAligned(MESSAGE_LENGTH, BitUtil.CACHE_LINE_LENGTH));

    System.err.println("MESSAGE_LENGTH = " + MESSAGE_LENGTH);

    Disposable sender =
        Flux.interval(Duration.ofMillis(100))
            .subscribe(
                i -> {
                  String request = "Hello to cluster " + i;

                  byte[] bytes = request.getBytes(StandardCharsets.UTF_8);
                  buffer.putBytes(0, request.getBytes(StandardCharsets.UTF_8));
                  long l = client.offer(buffer, 0, MESSAGE_LENGTH);

                  logger.info("Client: REQUEST {} send, result={}", i, l);
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
