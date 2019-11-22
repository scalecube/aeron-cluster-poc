package io.scalecube.acpoc.benchmarks;

import io.aeron.cluster.client.AeronCluster;
import io.aeron.cluster.client.EgressListener;
import io.aeron.logbuffer.Header;
import java.util.concurrent.TimeUnit;
import org.agrona.BitUtil;
import org.agrona.BufferUtil;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@Fork(
    value = 1,
    jvmArgs = {
      "-XX:+UnlockExperimentalVMOptions",
      "-XX:+TrustFinalNonStaticFields",
      "-XX:+UnlockDiagnosticVMOptions",
      "-XX:GuaranteedSafepointInterval=300000",
      "-XX:BiasedLockingStartupDelay=0",
      "-XX:+UseParallelOldGC",
      "-Dagrona.disable.bounds.checks=true",
      "-Daeron.term.buffer.sparse.file=false",
      "-Daeron.threading.mode=SHARED",
      "-Daeron.archive.threading.mode=SHARED",
      "-Daeron.archive.file.sync.level=0",
      "-Daeron.archive.segment.file.length=1g",
      "-Daeron.archive.control.mtu.length=4k",
      "-Daeron.spies.simulate.connection=true"
    })
@Threads(1)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 15)
public class ClusterRoundTripBenchmark {

  private static final int OPERATIONS_PER_INVOCATION = 1000;

  /**
   * Benchmark.
   *
   * @param state state
   * @param blackhole blackhole
   */
  @Benchmark
  @OutputTimeUnit(TimeUnit.NANOSECONDS)
  @BenchmarkMode({Mode.SampleTime})
  @OperationsPerInvocation(OPERATIONS_PER_INVOCATION)
  public void testTime(BenchmarkState state, Blackhole blackhole) {
    for (int i = 0; i < OPERATIONS_PER_INVOCATION; i++) {
      blackhole.consume(state.run());
    }
  }

  /**
   * Benchmark.
   *
   * @param state state
   * @param blackhole blackhole
   */
  @Benchmark
  @OutputTimeUnit(TimeUnit.SECONDS)
  @BenchmarkMode({Mode.Throughput})
  @OperationsPerInvocation(OPERATIONS_PER_INVOCATION)
  public void testTps(BenchmarkState state, Blackhole blackhole) {
    for (int i = 0; i < OPERATIONS_PER_INVOCATION; i++) {
      blackhole.consume(state.run());
    }
  }

  @State(Scope.Benchmark)
  public static class BenchmarkState implements EgressListener {

    @Param({"256"})
    private int messageLength;

    private ClusterNode clusterNode;
    private ClusterClient clusterClient;
    private AeronCluster client;
    private UnsafeBuffer offerBuffer;
    private boolean receivedResponse;

    long run() {
      receivedResponse = false;
      long result;
      do {
        result = client.offer(offerBuffer, 0, messageLength);
      } while (result <= 0);
      while (!receivedResponse) {
        client.pollEgress();
      }
      return result;
    }

    /** Setup method. */
    @Setup
    public void setUp() {
      clusterNode = ClusterNode.launch();
      clusterClient = ClusterClient.launch(this);
      client = clusterClient.client();

      offerBuffer =
          new UnsafeBuffer(
              BufferUtil.allocateDirectAligned(messageLength, BitUtil.CACHE_LINE_LENGTH));
    }

    @Override
    public void onMessage(
        long clusterSessionId,
        long timestamp,
        DirectBuffer buffer,
        int offset,
        int length,
        Header header) {
      receivedResponse = true;
    }

    /** Tear down method. */
    @TearDown
    public void tearDown() {
      CloseHelper.quietClose(clusterClient);
      CloseHelper.quietClose(clusterNode);
    }
  }
}
