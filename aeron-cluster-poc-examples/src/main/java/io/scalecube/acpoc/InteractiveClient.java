package io.scalecube.acpoc;

import io.aeron.cluster.client.AeronCluster;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import org.agrona.CloseHelper;
import org.agrona.IoUtil;
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
  private static final BiConsumer<String, AeronCluster> stringSender = (str, client) -> {
    String request = "REQ:" + str;
    byte[] bytes = request.getBytes(StandardCharsets.UTF_8);
    UnsafeBuffer buffer = new UnsafeBuffer(bytes);
    long l = client.offer(buffer, 0, bytes.length);
    logger.info("Client: REQUEST {} sent, result={}", request, l);
  };

  /**
   * Main method.
   *
   * @param args program arguments.
   */
  public static void main(String[] args) {
    String clientId = "client-" + Utils.instanceId();
    String clientDirName = Paths.get(IoUtil.tmpDirName(), "aeron", "cluster", clientId).toString();

    if (Configurations.CLEAN_START) {
      IoUtil.delete(new File(clientDirName), true);
    }

    System.out.println("Cluster client directory: " + clientDirName);
    startClient(clientDirName);

    Executors.newSingleThreadExecutor().submit(inputPollJob());

    receiver = Flux.interval(Duration.ofMillis(100)) //
        .subscribe(i -> client.pollEgress());
    Mono<Void> onShutdown = Utils.onShutdown(shutdownHook(clientDirName));
    onShutdown.block();
  }

  private static Runnable inputPollJob() {
    return () -> {
      while (true) {
        Scanner scanner = new Scanner(System.in);
        System.out
            .println("Type request body and press enter to send to Aeron cluster. Q to quit... ");
        String payload = scanner.nextLine();
        if ("Q".equals(payload)) {
          break;
        }
        stringSender.accept(payload, client);
      }
      System.exit(0);
    };

  }

  private static Callable shutdownHook(String clientDirName) {
    return () -> {
      System.out.println("Shutting down");
      receiver.dispose();
      CloseHelper.close(client);
      CloseHelper.close(clientMediaDriver);
      if (Configurations.CLEAN_SHUTDOWN) {
        IoUtil.delete(new File(clientDirName), true);
      }
      return null;
    };
  }

  private static void startClient(String clientDirName) {
    System.out.println("Client starting.");
    clientMediaDriver = MediaDriver.launch(
        new MediaDriver.Context()
            .threadingMode(ThreadingMode.SHARED)
            .warnIfDirectoryExists(true)
            .aeronDirectoryName(clientDirName));
    client =
        AeronCluster.connect(
            new AeronCluster.Context()
                .errorHandler(ex -> logger.error("Exception occurred: " + ex, ex))
                .egressListener(new EgressListenerImpl())
                .aeronDirectoryName(clientDirName)
                .ingressChannel("aeron:udp"));
    System.out.println("Client started.");
  }
}
