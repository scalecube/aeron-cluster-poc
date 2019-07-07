package io.scalecube.acpoc.benchmarks;

import io.aeron.cluster.client.AeronCluster;
import io.aeron.cluster.client.EgressListener;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.MediaDriver.Context;
import io.aeron.driver.ThreadingMode;
import io.aeron.logbuffer.Header;
import io.scalecube.acpoc.Configurations;
import io.scalecube.acpoc.Utils;
import java.io.File;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.HdrHistogram.Recorder;
import org.agrona.BitUtil;
import org.agrona.BufferUtil;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.IoUtil;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.YieldingIdleStrategy;
import org.agrona.console.ContinueBarrier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

/** Runner to start the cluster client that continuously sends requests to cluster. */
public class PingPongBenchmark {

  private static final Logger logger = LoggerFactory.getLogger(PingPongBenchmark.class);

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
    String clientDirName = Paths.get(IoUtil.tmpDirName(), "aeron", "cluster", clientId).toString();

    if (Configurations.CLEAN_START) {
      IoUtil.delete(new File(clientDirName), true);
    }

    System.out.println("Cluster client directory: " + clientDirName);

    MediaDriver clientMediaDriver =
        MediaDriver.launch(
            new Context()
                .threadingMode(ThreadingMode.SHARED)
                .warnIfDirectoryExists(true)
                .aeronDirectoryName(clientDirName));

    EgressListener egressListener =
        (clusterSessionId, timestampMs, buffer, offset, length, header) ->
            pongHandler(buffer, offset, length, header);

    AeronCluster client =
        AeronCluster.connect(
            new AeronCluster.Context()
                .errorHandler(ex -> logger.error("Exception occurred: " + ex, ex))
                .egressListener(egressListener)
                .aeronDirectoryName(clientDirName)
                .ingressChannel("aeron:udp"));

    Mono<Void> onShutdown =
        Utils.onShutdown(
            () -> {
              CloseHelper.close(client);
              CloseHelper.close(clientMediaDriver);
              if (Configurations.CLEAN_SHUTDOWN) {
                IoUtil.delete(new File(clientDirName), true);
              }
              return null;
            });
    onShutdown.subscribe();

    Thread.sleep(100);
    final ContinueBarrier barrier = new ContinueBarrier("Execute again?");

    do {
      System.out.println("Pinging " + NUMBER_OF_MESSAGES + " messages");
      roundTripMessages(client);
      System.out.println("Histogram of RTT latencies in microseconds.");
    } while (barrier.await());
  }

  private static void roundTripMessages(AeronCluster client) {
    HISTOGRAM.reset();

    Disposable reporter = latencyReporter.start();

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

    Mono.delay(Duration.ofMillis(100)).doOnSubscribe(s -> reporter.dispose()).then().subscribe();
  }

  private static void pongHandler(
      final DirectBuffer buffer, final int offset, final int length, final Header header) {
    final long pingTimestamp = buffer.getLong(offset);
    final long rttNs = System.nanoTime() - pingTimestamp;

    HISTOGRAM.recordValue(rttNs);
  }
}
