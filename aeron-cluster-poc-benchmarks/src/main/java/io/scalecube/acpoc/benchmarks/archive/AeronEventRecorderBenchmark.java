package io.scalecube.acpoc.benchmarks.archive;

import com.om2.exchange.market.service.OrderEventRecorder;
import com.om2.exchange.market.service.OrderEventRecorderImpl;
import com.om2.exchange.market.service.api.OrderSide;
import com.om2.exchange.market.service.api.OrderType;
import com.om2.exchange.market.service.api.TimeInForce;
import io.aeron.Aeron;
import io.aeron.ChannelUri;
import io.aeron.CommonContext;
import io.aeron.Publication;
import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.archive.client.AeronArchive;
import io.aeron.archive.codecs.SourceLocation;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.MediaDriver.Context;
import io.aeron.driver.ThreadingMode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.agrona.CloseHelper;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@Fork(value = 1)
@Threads(1)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 15)
public class AeronEventRecorderBenchmark {

  private static final int OPERATIONS_PER_INVOCATION = 1000;

  @State(Scope.Benchmark)
  public static class SharedState {

    private static final int RECORDING_STREAM_ID = 100500;

    public long matchId = randomNum();
    public int makerBrokerId = randomNum();
    public long makerBrokerOrderId = randomNum();
    public long makerOrderId = randomNum();
    public int takerBrokerId = randomNum();
    public long takerBrokerOrderId = randomNum();
    public long takerOrderId = randomNum();
    public OrderType takerOrderType = OrderType.Limit;
    private long eventId = randomNum();
    private String symbol = "USDXBTC";
    private int instrumentId = 42;
    private int brokerId = randomNum();
    private long brokerOrderId = randomNum();
    private long orderId = randomNum();
    private OrderSide side = OrderSide.Sell;
    private BigDecimal quantity = BigDecimal.valueOf(randomNum());
    private BigDecimal price = BigDecimal.valueOf(randomNum());
    private long timestamp = randomNum();
    private TimeInForce timeInForce = TimeInForce.GTC;
    private long expiryDate = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
    private String userId = "userId";

    private MediaDriver mediaDriver;
    private Aeron aeron;
    private Archive archive;
    private AeronArchive aeronArchive;
    private Publication publication;
    private OrderEventRecorder orderEventRecorder;

    /** Setup method. */
    @Setup
    public void setUp() {
      createAeronArchive();
      startAeronArchiveRecording();
      orderEventRecorder = new OrderEventRecorderImpl(publication, new BusySpinIdleStrategy());
    }

    private void createAeronArchive() {
      mediaDriver =
          MediaDriver.launch(
              new Context()
                  .dirDeleteOnStart(true)
                  .dirDeleteOnShutdown(true)
                  .threadingMode(ThreadingMode.SHARED));
      aeron = Aeron.connect(new Aeron.Context().preTouchMappedMemory(true));
      archive =
          Archive.launch(
              new Archive.Context()
                  .deleteArchiveOnStart(true)
                  .errorHandler(System.err::println)
                  .threadingMode(ArchiveThreadingMode.SHARED));
      aeronArchive = AeronArchive.connect(new AeronArchive.Context());
    }

    private void startAeronArchiveRecording() {
      publication = aeron.addPublication(CommonContext.IPC_CHANNEL, RECORDING_STREAM_ID);
      String channel = ChannelUri.addSessionId(CommonContext.IPC_CHANNEL, publication.sessionId());
      aeronArchive.startRecording(channel, RECORDING_STREAM_ID, SourceLocation.LOCAL);
    }

    /** Teardown method. */
    @TearDown
    public synchronized void tearDown() {
      CloseHelper.quietClose(publication);

      try {
        TimeUnit.NANOSECONDS.sleep(mediaDriver.context().publicationLingerTimeoutNs());
      } catch (InterruptedException e) {
        e.printStackTrace();
      }

      truncateRecording();

      CloseHelper.quietClose(aeron);
      CloseHelper.quietClose(aeronArchive);
      CloseHelper.quietClose(archive);
      CloseHelper.quietClose(mediaDriver);
    }

    private int randomNum() {
      return new Random().nextInt(100500);
    }

    private void truncateRecording() {
      if (aeronArchive == null) {
        return;
      }
      long recordingId =
          aeronArchive.findLastMatchingRecording(
              0, CommonContext.IPC_CHANNEL, RECORDING_STREAM_ID, publication.sessionId());
      aeronArchive.truncateRecording(recordingId, 0);
    }
  }

  @Benchmark
  @OutputTimeUnit(TimeUnit.NANOSECONDS)
  @BenchmarkMode({Mode.SampleTime})
  @OperationsPerInvocation(OPERATIONS_PER_INVOCATION)
  public void testOrderAddedEventTime(SharedState sharedState, Blackhole bh) {
    testOrderAddedEvent(sharedState, bh);
  }

  @Benchmark
  @OutputTimeUnit(TimeUnit.SECONDS)
  @BenchmarkMode({Mode.Throughput})
  @OperationsPerInvocation(OPERATIONS_PER_INVOCATION)
  public void testOrderAddedEventTps(SharedState sharedState, Blackhole bh) {
    testOrderAddedEvent(sharedState, bh);
  }

  @Benchmark
  @OutputTimeUnit(TimeUnit.NANOSECONDS)
  @BenchmarkMode({Mode.SampleTime})
  @OperationsPerInvocation(OPERATIONS_PER_INVOCATION)
  public void testOrderExecutedEventTime(SharedState sharedState, Blackhole bh) {
    testOrderExecutedEvent(sharedState, bh);
  }

  @Benchmark
  @OutputTimeUnit(TimeUnit.SECONDS)
  @BenchmarkMode({Mode.Throughput})
  @OperationsPerInvocation(OPERATIONS_PER_INVOCATION)
  public void testOrderExecutedEventTps(SharedState sharedState, Blackhole bh) {
    testOrderExecutedEvent(sharedState, bh);
  }

  @Benchmark
  @OutputTimeUnit(TimeUnit.NANOSECONDS)
  @BenchmarkMode({Mode.SampleTime})
  @OperationsPerInvocation(OPERATIONS_PER_INVOCATION)
  public void testOrderCancelledEventTime(SharedState sharedState, Blackhole bh) {
    testOrderCancelledEvent(sharedState, bh);
  }

  @Benchmark
  @OutputTimeUnit(TimeUnit.SECONDS)
  @BenchmarkMode({Mode.Throughput})
  @OperationsPerInvocation(OPERATIONS_PER_INVOCATION)
  public void testOrderCancelledEventTps(SharedState sharedState, Blackhole bh) {
    testOrderCancelledEvent(sharedState, bh);
  }

  private void testOrderAddedEvent(SharedState sharedState, Blackhole bh) {
    for (int i = 0; i < OPERATIONS_PER_INVOCATION; i++) {
      sharedState.orderEventRecorder.recordOrderAddedEvent(
          sharedState.eventId,
          sharedState.symbol,
          sharedState.instrumentId,
          sharedState.brokerId,
          sharedState.brokerOrderId,
          sharedState.orderId,
          sharedState.side,
          sharedState.quantity.unscaledValue().longValue(),
          sharedState.quantity.scale(),
          sharedState.price.unscaledValue().longValue(),
          sharedState.price.scale(),
          sharedState.timestamp,
          sharedState.timeInForce,
          sharedState.expiryDate,
          sharedState.userId);
      bh.consume(true);
    }
  }

  private void testOrderExecutedEvent(SharedState sharedState, Blackhole bh) {
    for (int i = 0; i < OPERATIONS_PER_INVOCATION; i++) {
      sharedState.orderEventRecorder.recordOrderExecutedEvent(
          sharedState.eventId,
          sharedState.symbol,
          sharedState.instrumentId,
          sharedState.matchId,
          sharedState.makerBrokerId,
          sharedState.makerBrokerOrderId,
          sharedState.makerOrderId,
          sharedState.takerBrokerId,
          sharedState.takerBrokerOrderId,
          sharedState.takerOrderId,
          sharedState.takerOrderType,
          sharedState.price.unscaledValue().longValue(),
          sharedState.price.scale(),
          sharedState.timeInForce,
          sharedState.expiryDate,
          sharedState.userId,
          sharedState.quantity.unscaledValue().longValue(),
          sharedState.quantity.scale(),
          sharedState.price.unscaledValue().longValue(),
          sharedState.price.scale(),
          sharedState.timestamp);
      bh.consume(true);
    }
  }

  private void testOrderCancelledEvent(SharedState sharedState, Blackhole bh) {
    for (int i = 0; i < OPERATIONS_PER_INVOCATION; i++) {
      sharedState.orderEventRecorder.recordOrderCancelledEvent(
          sharedState.eventId,
          sharedState.symbol,
          sharedState.instrumentId,
          sharedState.brokerId,
          sharedState.brokerOrderId,
          sharedState.orderId,
          sharedState.side,
          sharedState.quantity.unscaledValue().longValue(),
          sharedState.quantity.scale(),
          sharedState.timestamp,
          sharedState.timeInForce,
          sharedState.userId);
      bh.consume(true);
    }
  }
}
