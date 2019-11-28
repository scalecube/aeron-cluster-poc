package io.scalecube.acpoc.benchmarks.cluster;

import io.aeron.Publication;
import io.aeron.cluster.client.AeronCluster;
import io.aeron.cluster.client.EgressListener;
import io.aeron.logbuffer.Header;
import io.scalecube.acpoc.benchmarks.LatencyReporter;
import io.scalecube.acpoc.benchmarks.Runners;
import java.util.concurrent.TimeUnit;
import org.agrona.BitUtil;
import org.agrona.BufferUtil;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.UnsafeBuffer;

public class ClusterLatencyBenchmark {

  /**
   * Main method.
   *
   * @param args args
   */
  public static void main(String[] args) throws Exception {
    try (State state = new State()) {
      TimeUnit.MILLISECONDS.sleep(Runners.warmupTime().toMillis() * Runners.warmupIterations());
      TimeUnit.MILLISECONDS.sleep(
          Runners.measurementTime().toMillis() * Runners.measurementIterations());
    }
  }

  private static class State implements EgressListener, AutoCloseable {

    private ClusterNode clusterNode;
    private ClusterClient clusterClient;
    private SenderReceiverAgentRunner senderReceiverRunner;
    private LatencyReporter reporter;

    State() {
      try {
        start();
      } catch (Throwable th) {
        close();
        throw th;
      }
    }

    private void start() {
      clusterNode = ClusterNode.launch();
      clusterClient = ClusterClient.launch(this);
      reporter = LatencyReporter.launch(ClusterLatencyBenchmark.class);
      Agent senderAgent = new SenderAgent(clusterClient.client());
      Agent receiverAgent = new ReceiverAgent(clusterClient.client());
      senderReceiverRunner = SenderReceiverAgentRunner.launch(senderAgent, receiverAgent);
    }

    @Override
    public void onMessage(
        long clusterSessionId,
        long timestamp,
        DirectBuffer buffer,
        int offset,
        int length,
        Header header) {
      long start = buffer.getLong(offset);
      long diff = System.nanoTime() - start;
      reporter.onDiff(diff);
    }

    @Override
    public void close() {
      CloseHelper.quietCloseAll(reporter, senderReceiverRunner, clusterClient, clusterNode);
    }

    private static class SenderAgent implements Agent {

      private static final int MESSAGE_LENGTH = Runners.MESSAGE_LENGTH;

      private final AeronCluster client;
      private final UnsafeBuffer offerBuffer;

      private SenderAgent(AeronCluster client) {
        this.client = client;
        this.offerBuffer =
            new UnsafeBuffer(
                BufferUtil.allocateDirectAligned(MESSAGE_LENGTH, BitUtil.CACHE_LINE_LENGTH));
      }

      @Override
      public int doWork() {
        offerBuffer.putLong(0, System.nanoTime());
        long result = client.offer(offerBuffer, 0, MESSAGE_LENGTH);
        if (result > 0) {
          return 1;
        }
        checkResult(result);
        return 0;
      }

      private void checkResult(final long result) {
        if (result == Publication.NOT_CONNECTED
            || result == Publication.CLOSED
            || result == Publication.MAX_POSITION_EXCEEDED) {
          throw new IllegalStateException("unexpected publication state: " + result);
        }
        if (Thread.currentThread().isInterrupted()) {
          throw new IllegalStateException("Thread.currentThread().isInterrupted()");
        }
      }

      @Override
      public String roleName() {
        return "SenderAgent";
      }
    }

    private static class ReceiverAgent implements Agent {

      private final AeronCluster client;

      private ReceiverAgent(AeronCluster client) {
        this.client = client;
      }

      @Override
      public int doWork() {
        return client.pollEgress();
      }

      @Override
      public String roleName() {
        return "ReceiverAgent";
      }
    }
  }
}
