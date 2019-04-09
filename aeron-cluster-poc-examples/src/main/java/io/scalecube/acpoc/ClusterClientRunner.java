package io.scalecube.acpoc;

import io.scalecube.acpoc.ClusterClient.OnResponseListener;
import java.io.File;
import java.nio.file.Paths;
import java.time.Duration;
import org.agrona.IoUtil;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Runner to start the cluster client that continuously sends requests to cluster. */
public class ClusterClientRunner {

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

    OnResponseListener onResponseListener =
        (buffer, offset, length) -> {
          String content = buffer.getStringWithoutLengthUtf8(offset, length);
          System.out.println("Client: received " + content);
        };

    ClusterClient client = new ClusterClient(clientDirName, onResponseListener);

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
              if (Configurations.CLEAN_SHUTDOWN) {
                IoUtil.delete(new File(clientDirName), true);
              }
              return null;
            });
    onShutdown.block();
  }
}
