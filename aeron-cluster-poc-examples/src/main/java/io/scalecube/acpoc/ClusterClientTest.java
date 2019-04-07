package io.scalecube.acpoc;

import io.aeron.CommonContext;
import io.scalecube.acpoc.ClusterClient.OnResponseListener;
import java.time.Duration;
import reactor.core.publisher.Flux;

/**
 * Runner to start the cluster client that continuously sends requests to cluster.
 */
public class ClusterClientTest {

  /**
   * Main method.
   *
   * @param args program arguments.
   */
  public static void main(String[] args) throws InterruptedException {
    String baseDirName =
        CommonContext.getAeronDirectoryName() + "-" + "client" + "-" + System.currentTimeMillis();

    OnResponseListener onResponseListener = (buffer, offset, length) -> System.out
        .println("Client: received " + length + " bytes.");

    ClusterClient client = new ClusterClient(baseDirName, onResponseListener);
    Flux.interval(Duration.ofSeconds(2)).subscribe(cnt -> {
      client.sendMessage("Hello to cluster " + cnt);
      client.awaitResponses(1);
    });
    Thread.currentThread().join();
  }

}
