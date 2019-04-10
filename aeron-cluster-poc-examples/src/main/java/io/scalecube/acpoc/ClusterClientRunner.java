package io.scalecube.acpoc;

import io.aeron.cluster.client.AeronCluster;
import io.aeron.cluster.client.EgressListener;
import io.aeron.cluster.codecs.EventCode;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import io.aeron.logbuffer.Header;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Duration;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.IoUtil;
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
    String clientId = "client-" + Utils.instanceId();
    String clientDirName = Paths.get(IoUtil.tmpDirName(), "aeron", "cluster", clientId).toString();

    if (Configurations.CLEAN_START) {
      IoUtil.delete(new File(clientDirName), true);
    }

    System.out.println("Cluster client directory: " + clientDirName);

    MediaDriver clientMediaDriver =
        MediaDriver.launch(
            new MediaDriver.Context()
                .threadingMode(ThreadingMode.SHARED)
                .warnIfDirectoryExists(true)
                .aeronDirectoryName(clientDirName));

    AeronCluster client =
        AeronCluster.connect(
            new AeronCluster.Context()
                .errorHandler(ex -> logger.error("Exception occurred: " + ex, ex))
                .egressListener(new EgressListenerImpl())
                .aeronDirectoryName(clientDirName)
                .ingressChannel("aeron:udp"));

    Disposable sender =
        Flux.interval(Duration.ofSeconds(1))
            .subscribe(
                i -> {
                  String request = "Hello to cluster " + i;

                  byte[] bytes = request.getBytes(StandardCharsets.UTF_8);
                  UnsafeBuffer buffer = new UnsafeBuffer(bytes);
                  long l = client.offer(buffer, 0, bytes.length);

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
              if (Configurations.CLEAN_SHUTDOWN) {
                IoUtil.delete(new File(clientDirName), true);
              }
              return null;
            });
    onShutdown.block();
  }

  public static class EgressListenerImpl implements EgressListener {

    @Override
    public void onMessage(
        long clusterSessionId,
        long timestamp,
        DirectBuffer buffer,
        int offset,
        int length,
        Header header) {
      logger.info(
          "[onMessage]: timestamp: {}; from clusterSession: {}, position: {}, content: '{}'",
          timestamp,
          clusterSessionId,
          header.position(),
          buffer.getStringWithoutLengthAscii(offset, length));
    }

    @Override
    public void sessionEvent(
        long correlationId,
        long clusterSessionId,
        long leadershipTermId,
        int leaderMemberId,
        EventCode code,
        String detail) {
      logger.info(
          "[onSessionEvent]: correlationId: {}, clusterSessionId: {}, "
              + "leadershipTermId: {}, leaderMemberId: {}, eventCode: {}, detail: {}",
          correlationId,
          clusterSessionId,
          leadershipTermId,
          leaderMemberId,
          code,
          detail);
    }

    @Override
    public void newLeader(
        long clusterSessionId, long leadershipTermId, int leaderMemberId, String memberEndpoints) {
      logger.info(
          "[newLeader]: clusterSessionId: {}, "
              + "leadershipTermId: {}, leaderMemberId: {}, memberEndpoints: {}",
          clusterSessionId,
          leadershipTermId,
          leaderMemberId,
          memberEndpoints);
    }
  }
}
