package io.scalecube.acpoc;

import io.aeron.CommonContext;
import io.scalecube.acpoc.ClusterClient.OnResponseListener;
import java.time.Duration;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Runner to start the cluster client that continuously sends requests to cluster. */
public class ClusterClientTest {

  /**
   * Main method.
   *
   * @param args program arguments.
   */
  public static void main(String[] args) throws InterruptedException {
    String baseDirName =
        CommonContext.getAeronDirectoryName() + "-" + "client" + "-" + System.currentTimeMillis();

    OnResponseListener onResponseListener =
        (buffer, offset, length) -> {
          String content = buffer.getStringWithoutLengthUtf8(offset, length);
          System.out.println("Client: received " + content);
        };

    ClusterClient client = new ClusterClient(baseDirName, onResponseListener);

    Disposable disposable =
        Flux.interval(Duration.ofSeconds(1))
            .subscribe(
                cnt -> {
                  long l = client.sendMessage("Hello to cluster " + cnt);
                  System.out.println("sendMessage result: " + l);
                  client.poll();
                });

    Mono<Void> onShutdown =
        Utils.onShutdown(
            () -> {
              disposable.dispose();
              client.close();
              return null;
            });
    onShutdown.block();
  }
}
