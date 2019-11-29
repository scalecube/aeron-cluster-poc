package io.scalecube.acpoc;

import io.aeron.cluster.client.AeronCluster;
import io.aeron.driver.DefaultAllowTerminationValidator;
import io.aeron.driver.MediaDriver;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import org.agrona.CloseHelper;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class InteractiveClient {

  private static final Logger logger = LoggerFactory.getLogger(InteractiveClient.class);

  private static MediaDriver clientMediaDriver;
  private static AeronCluster client;
  private static Disposable receiver;

  private static final BiConsumer<String, AeronCluster> stringSender =
      (str, client) -> {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        UnsafeBuffer buffer = new UnsafeBuffer(bytes);
        long l = client.offer(buffer, 0, bytes.length);
        if (l > 0) {
          logger.info("Client: REQUEST '{}' sent, result={}", str, l);
        }
      };

  /**
   * Main method.
   *
   * @param args program arguments.
   */
  public static void main(String[] args) {
    startClient();

    Executors.newSingleThreadExecutor().submit(inputPollJob());

    receiver =
        Flux.interval(Duration.ofMillis(100)) //
            .subscribe(i -> client.pollEgress());
    Mono<Void> onShutdown = Utils.onShutdown(shutdownHook());
    onShutdown.block();
  }

  private static Runnable inputPollJob() {
    return () -> {
      while (true) {
        Scanner scanner = new Scanner(System.in);
        System.out.println(
            "Type request body and press enter to send to Aeron cluster. Q to quit... ");
        String payload = scanner.nextLine();
        if ("Q".equals(payload)) {
          client.close();
          break;
        }
        stringSender.accept(payload, client);
      }
      System.exit(0);
    };
  }

  private static Callable shutdownHook() {
    return () -> {
      System.out.println("Shutting down");
      receiver.dispose();
      CloseHelper.close(client);
      CloseHelper.close(clientMediaDriver);
      return null;
    };
  }

  private static void startClient() {
    System.out.println("Client starting.");
    clientMediaDriver =
        MediaDriver.launch(
            new MediaDriver.Context()
                .errorHandler(ex -> logger.error("Exception occurred at MediaDriver: ", ex))
                .terminationHook(() -> logger.info("TerminationHook called on MediaDriver "))
                .terminationValidator(new DefaultAllowTerminationValidator())
                .warnIfDirectoryExists(true)
                .dirDeleteOnStart(true)
                .dirDeleteOnShutdown(true));
    client =
        AeronCluster.connect(
            new AeronCluster.Context()
                .errorHandler(ex -> logger.error("Exception occurred at AeronCluster: ", ex))
                .egressListener(new EgressListenerImpl())
                .aeronDirectoryName(clientMediaDriver.aeronDirectoryName())
                .ingressChannel("aeron:udp"));
    System.out.println("Client started.");
  }
}
