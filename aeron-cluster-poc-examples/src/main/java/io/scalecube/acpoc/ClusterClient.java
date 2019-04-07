package io.scalecube.acpoc;

import io.aeron.cluster.client.AeronCluster;
import io.aeron.cluster.client.EgressListener;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.MediaDriver.Context;
import io.aeron.driver.ThreadingMode;
import org.agrona.BitUtil;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClusterClient implements AutoCloseable {

  private static final Logger logger = LoggerFactory.getLogger(ClusterClient.class);

  private final MediaDriver clientMediaDriver;
  private final AeronCluster client;
  private int responseCount;

  public ClusterClient(final String aeronDirName, OnResponseListener onResponse) {
    EgressListener egressMessageListener =
        (clusterSessionId, timestamp, buffer, offset, length, header) -> {
          logger.info(
              "[Received]: timestamp: {}; from clusterSession: {}, position: {}, content: {}",
              timestamp,
              clusterSessionId,
              header.position(),
              buffer.getStringWithoutLengthAscii(offset, length));
          responseCount++;
          onResponse.onResponse(buffer, offset, length);
        };

    this.clientMediaDriver = MediaDriver.launch(
        new Context()
            .threadingMode(ThreadingMode.SHARED)
            .aeronDirectoryName(aeronDirName));

    this.client = AeronCluster.connect(
        new AeronCluster.Context()
            .egressListener(egressMessageListener)
            .aeronDirectoryName(aeronDirName)
            .ingressChannel("aeron:udp"));
  }

  public void sendMessage(final String msg) {
    final ExpandableArrayBuffer msgBuffer = new ExpandableArrayBuffer();
    msgBuffer.putStringWithoutLengthAscii(0, msg);
    while (client.offer(msgBuffer, 0, BitUtil.SIZE_OF_INT) < 0) {
      Utils.checkInterruptedStatus();
      client.pollEgress();
      Thread.yield();
    }
    client.pollEgress();
  }

  public void awaitResponses(final int messageCount) {
    while (responseCount < messageCount) {
      Utils.checkInterruptedStatus();
      Thread.yield();
      client.pollEgress();
    }
  }

  public void close() {
    CloseHelper.close(client);
    CloseHelper.close(clientMediaDriver);
    if (null != clientMediaDriver) {
      clientMediaDriver.context().deleteAeronDirectory();
    }
  }

  @FunctionalInterface
  public interface OnResponseListener {

    void onResponse(DirectBuffer buffer, int offset, int length);
  }
}
