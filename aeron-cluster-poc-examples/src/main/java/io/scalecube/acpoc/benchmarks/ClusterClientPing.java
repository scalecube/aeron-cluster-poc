package io.scalecube.acpoc.benchmarks;

import io.aeron.CommonContext;
import io.aeron.cluster.client.AeronCluster;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.MediaDriver.Context;
import io.aeron.logbuffer.Header;
import io.scalecube.acpoc.Utils;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import org.HdrHistogram.Recorder;
import org.agrona.BitUtil;
import org.agrona.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.YieldingIdleStrategy;
import org.agrona.console.ContinueBarrier;
import reactor.core.Disposable;

/** Runner to start the cluster client that continuously sends requests to cluster. */
public class ClusterClientPing {

  private static final int MESSAGE_LENGTH = BenchmarkConfigurations.MESSAGE_LENGTH;
  private static final long NUMBER_OF_MESSAGES = BenchmarkConfigurations.NUMBER_OF_MESSAGES;

  private static final int REQUESTED = BenchmarkConfigurations.REQUESTED;

  private static final UnsafeBuffer OFFER_BUFFER =
      new UnsafeBuffer(BufferUtil.allocateDirectAligned(MESSAGE_LENGTH, BitUtil.CACHE_LINE_LENGTH));

  private static final Recorder HISTOGRAM = new Recorder(TimeUnit.SECONDS.toNanos(10), 3);
  private static final LatencyReporter latencyReporter = new LatencyReporter(HISTOGRAM);
  private static final IdleStrategy IDLE_STRATEGY = new YieldingIdleStrategy();

  /**
   * Main method.
   *
   * @param args program arguments.
   */
  public static void main(String[] args) throws InterruptedException {
    String clientId = "client-benchmark" + Utils.instanceId();
    String clientDirName =
        Paths.get(CommonContext.getAeronDirectoryName(), "aeron", "cluster", clientId).toString();
    System.out.println("Cluster client directory: " + clientDirName);

    try (MediaDriver clientMediaDriver =
            MediaDriver.launch(
                new Context()
                    .aeronDirectoryName(clientDirName)
                    .warnIfDirectoryExists(true)
                    .dirDeleteOnStart(true)
                    .dirDeleteOnShutdown(true)
                    .printConfigurationOnStart(true)
                    .errorHandler(Throwable::printStackTrace));
        AeronCluster client =
            AeronCluster.connect(
                new AeronCluster.Context()
                    .egressListener(
                        (clusterSessionId, timestampMs, buffer, offset, length, header) ->
                            pongHandler(buffer, offset, length, header))
                    .aeronDirectoryName(clientMediaDriver.aeronDirectoryName())
                    .ingressChannel("aeron:udp")
                    .errorHandler(Throwable::printStackTrace))) {

      Thread.sleep(100);
      ContinueBarrier barrier = new ContinueBarrier("Execute again?");

      do {
        System.out.println("Pinging " + NUMBER_OF_MESSAGES + " messages");
        Disposable reporterDisposable = latencyReporter.start();
        roundTripMessages(client);
        Thread.sleep(100);
        reporterDisposable.dispose();
        System.out.println("Histogram of RTT latencies in microseconds.");
      } while (barrier.await());
    }
  }

  private static void roundTripMessages(AeronCluster client) {
    HISTOGRAM.reset();

    int produced = 0;
    int received = 0;

    for (long i = 0; i < NUMBER_OF_MESSAGES; ) {
      int workCount = 0;

      if (produced < REQUESTED) {
        OFFER_BUFFER.putLong(0, System.nanoTime());

        final long offeredPosition = client.offer(OFFER_BUFFER, 0, MESSAGE_LENGTH);

        if (offeredPosition > 0) {
          i++;
          workCount = 1;
          produced++;
        }
      }

      final int poll = client.pollEgress();

      workCount += poll;
      received += poll;
      produced -= poll;

      IDLE_STRATEGY.idle(workCount);
    }

    while (received < NUMBER_OF_MESSAGES) {
      final int poll = client.pollEgress();
      received += poll;
      IDLE_STRATEGY.idle(poll);
    }
  }

  private static void pongHandler(
      final DirectBuffer buffer, final int offset, final int length, final Header header) {
    final long pingTimestamp = buffer.getLong(offset);
    final long rttNs = System.nanoTime() - pingTimestamp;

    HISTOGRAM.recordValue(rttNs);
  }
}
