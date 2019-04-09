package io.scalecube.acpoc;

import io.aeron.cluster.client.AeronCluster;
import io.aeron.cluster.client.EgressListener;
import io.aeron.cluster.codecs.EventCode;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.MediaDriver.Context;
import io.aeron.driver.ThreadingMode;
import io.aeron.logbuffer.Header;
import java.nio.charset.StandardCharsets;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wrapper around {@link AeronCluster} cluster client. Aims to ease the communication with
 * aeron-cluster.
 */
public class ClusterClient implements AutoCloseable {

  private static final Logger logger = LoggerFactory.getLogger(ClusterClient.class);

  private final MediaDriver clientMediaDriver;
  private final AeronCluster client;

  class EgressListenerImpl implements EgressListener {
    private final OnResponseListener onResponse;

    EgressListenerImpl(OnResponseListener onResponse) {
      this.onResponse = onResponse;
    }

    @Override
    public void onMessage(
        long clusterSessionId,
        long timestamp,
        DirectBuffer buffer,
        int offset,
        int length,
        Header header) {
      logger.info(
          "[onMessage]: timestamp: {}; from clusterSession: {}, position: {}, content: {}",
          timestamp,
          clusterSessionId,
          header.position(),
          buffer.getStringWithoutLengthAscii(offset, length));
      onResponse.onResponse(buffer, offset, length);
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
      // client.onNewLeader(clusterSessionId, leadershipTermId, leaderMemberId, memberEndpoints);
    }
  }

  /**
   * Creates an instance of client. Note that cluster member addresses are expected to be passed in
   * VM args as {@code aeron.cluster.member.endpoints}.
   *
   * @param aeronDirName directory name to be used for client's aeron media driver.
   * @param onResponse callback for response received from cluster.
   */
  public ClusterClient(final String aeronDirName, OnResponseListener onResponse) {
    this.clientMediaDriver =
        MediaDriver.launch(
            new Context()
                .threadingMode(ThreadingMode.SHARED)
                .warnIfDirectoryExists(true)
                .aeronDirectoryName(aeronDirName));

    this.client =
        AeronCluster.connect(
            new AeronCluster.Context()
                .errorHandler(ex -> logger.error("Exception occurred: " + ex, ex))
                .egressListener(new EgressListenerImpl(onResponse))
                .aeronDirectoryName(aeronDirName)
                .ingressChannel("aeron:udp"));
  }

  /**
   * Send message to cluster.
   *
   * @param msg - to be sent
   * @return result
   */
  public long sendMessage(final String msg) {
    byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
    UnsafeBuffer buffer = new UnsafeBuffer(bytes);
    return client.offer(buffer, 0, bytes.length);
  }

  /** Await responses from cluster. */
  public void poll() {
    client.pollEgress();
  }

  /** Shutdown method. */
  public void close() {
    CloseHelper.close(client);
    CloseHelper.close(clientMediaDriver);
  }

  /** Represents response's callback. */
  @FunctionalInterface
  public interface OnResponseListener {

    void onResponse(DirectBuffer buffer, int offset, int length);
  }
}
