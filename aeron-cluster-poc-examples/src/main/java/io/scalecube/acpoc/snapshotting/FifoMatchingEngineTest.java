package io.scalecube.acpoc.snapshotting;

import static io.aeron.Aeron.NULL_VALUE;
import static io.aeron.archive.codecs.SourceLocation.LOCAL;
import static io.scalecube.acpoc.Utils.checkInterruptedStatus;
import static org.agrona.concurrent.status.CountersReader.NULL_COUNTER_ID;

import io.aeron.Aeron;
import io.aeron.ChannelUri;
import io.aeron.CommonContext;
import io.aeron.Image;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.archive.ArchivingMediaDriver;
import io.aeron.archive.client.AeronArchive;
import io.aeron.archive.status.RecordingPos;
import io.aeron.cluster.client.ClusterException;
import io.aeron.cluster.service.ClientSession;
import io.aeron.cluster.service.Cluster;
import io.aeron.cluster.service.ClusteredServiceContainer.Context;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import io.scalecube.acpoc.Configurations;
import java.io.File;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import om2.exchange.marketdata.match.fifo.snapshotting.OrderSide;
import om2.exchange.marketdata.match.fifo.snapshotting.OrderType;
import org.agrona.IoUtil;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.YieldingIdleStrategy;
import org.agrona.concurrent.status.CountersReader;

public class FifoMatchingEngineTest {

  private static final Random RANDOM = new Random();
  private static final int ORDERS_COUNT = RANDOM.nextInt(100);
  private static final IdleStrategy IDLE_STRATEGY = new YieldingIdleStrategy();

  /** Starter. */
  public static void main(String[] args) {
    String instrumentId = UUID.randomUUID().toString();

    Map<Long, PriceLevel> bids = new HashMap<>();
    Map<Long, PriceLevel> asks = new HashMap<>();

    PriceLevel priceLevel = newPriceLevel();
    for (int i = 0; i < ORDERS_COUNT; i++) {
      PriceLevel target = RANDOM.nextBoolean() ? newPriceLevel() : priceLevel;
      Order order = newOrder(target);
      target.add(order);
      if (target.side == OrderSide.Buy) {
        bids.put(target.price, target);
      } else {
        asks.put(target.price, target);
      }
    }

    FifoMatchingEngine engine = new FifoMatchingEngine(instrumentId, bids, asks);

    System.out.println("before: " + engine);

    String nodeDirName = Paths.get(IoUtil.tmpDirName(), "aeron", "test").toString();
    if (Configurations.CLEAN_START) {
      IoUtil.delete(new File(nodeDirName), true);
    }
    System.out.println("node directory: " + nodeDirName);

    String aeronDirectoryName = Paths.get(nodeDirName, "media").toString();

    try (ArchivingMediaDriver archivingMediaDriver =
            ArchivingMediaDriver.launch(
                new MediaDriver.Context()
                    .threadingMode(ThreadingMode.SHARED)
                    .spiesSimulateConnection(true)
                    .errorHandler(Throwable::printStackTrace)
                    .aeronDirectoryName(aeronDirectoryName)
                    .dirDeleteOnStart(true),
                new Archive.Context()
                    .aeronDirectoryName(aeronDirectoryName)
                    .archiveDir(new File(nodeDirName, "archive"))
                    .threadingMode(ArchiveThreadingMode.SHARED)
                    .errorHandler(Throwable::printStackTrace)
                    .fileSyncLevel(0)
                    .deleteArchiveOnStart(true));
        AeronArchive aeronArchive =
            AeronArchive.connect(
                new AeronArchive.Context().aeronDirectoryName(aeronDirectoryName))) {

      Aeron aeron = aeronArchive.context().aeron();

      final String snapshotChannel = CommonContext.IPC_CHANNEL;
      final int snapshotStreamId = 106;

      Publication publication = aeron.addExclusivePublication(snapshotChannel, snapshotStreamId);
      final String channel = ChannelUri.addSessionId(snapshotChannel, publication.sessionId());

      final long subscriptionId = aeronArchive.startRecording(channel, snapshotStreamId, LOCAL);

      final int sessionId = publication.sessionId();
      final CountersReader counters = aeron.countersReader();

      final long recordingId;
      try {
        final int counterId = awaitRecordingCounter(sessionId, counters);
        recordingId = RecordingPos.getRecordingId(counters, counterId);
        System.out.println("recordingId = " + recordingId);

        engine.takeSnapshot(mockCluster(), publication);

        awaitRecordingComplete(aeronArchive, publication, counters, counterId, recordingId);
      } finally {
        aeronArchive.stopRecording(subscriptionId);
      }

      System.err.println(1);
      final int replaySessionId =
          (int)
              aeronArchive.startReplay(
                  recordingId, 0, NULL_VALUE, snapshotChannel, snapshotStreamId);
      final String replaySessionChannel = ChannelUri.addSessionId(snapshotChannel, replaySessionId);

      try (Subscription subscription =
          aeron.addSubscription(
              replaySessionChannel,
              snapshotStreamId,
              image -> System.out.println("+image: " + image.sessionId()),
              image -> System.out.println("-image: " + image.sessionId()))) {
        Image image = awaitImage(replaySessionId, subscription);
        System.err.println(2);
        FifoMatchingEngine matchingEngine = awaitMatchingEngine(image);
        System.err.println(3);
        System.err.println(matchingEngine);
      }
    }
  }

  private static FifoMatchingEngine awaitMatchingEngine(Image image) {

    MatchingEngineSnapshotLoader snapshotLoader = new MatchingEngineSnapshotLoader(image);

    while (true) {
      final int fragments = snapshotLoader.poll();
      if (snapshotLoader.isDone()) {
        break;
      }

      if (fragments == 0) {
        checkInterruptedStatus();

        if (image.isClosed()) {
          throw new RuntimeException("snapshot ended unexpectedly");
        }

        IDLE_STRATEGY.idle(fragments);
      }
    }

    return snapshotLoader.matchingEngine();
  }

  private static int awaitRecordingCounter(final int sessionId, final CountersReader counters) {
    IDLE_STRATEGY.reset();
    int counterId = RecordingPos.findCounterIdBySession(counters, sessionId);
    while (NULL_COUNTER_ID == counterId) {
      checkInterruptedStatus();
      IDLE_STRATEGY.idle();
      counterId = RecordingPos.findCounterIdBySession(counters, sessionId);
    }

    return counterId;
  }

  private static void awaitRecordingComplete(
      AeronArchive aeronArchive,
      Publication publication,
      CountersReader counters,
      int counterId,
      long recordingId) {
    IDLE_STRATEGY.reset();
    final long position = publication.position();
    do {
      IDLE_STRATEGY.idle();
      checkInterruptedStatus();

      if (!RecordingPos.isActive(counters, counterId, recordingId)) {
        throw new ClusterException("recording has stopped unexpectedly: " + recordingId);
      }

      aeronArchive.checkForErrorResponse();
    } while (counters.getCounterValue(counterId) < position);
  }

  private static Image awaitImage(final int sessionId, final Subscription subscription) {
    IDLE_STRATEGY.reset();
    Image image;
    while ((image = subscription.imageBySessionId(sessionId)) == null) {
      checkInterruptedStatus();
      IDLE_STRATEGY.idle();
    }

    return image;
  }

  private static PriceLevel newPriceLevel() {
    OrderSide side = RANDOM.nextBoolean() ? OrderSide.Buy : OrderSide.Sell;
    long price = RANDOM.nextLong();
    return new PriceLevel(side, price);
  }

  private static Order newOrder(PriceLevel priceLevel) {
    UUID externalId = UUID.randomUUID();
    long quantity = RANDOM.nextLong();
    long remainingQuantity = RANDOM.nextLong();
    OrderType orderType = RANDOM.nextBoolean() ? OrderType.Limit : OrderType.Market;
    boolean isMarketMaker = RANDOM.nextBoolean();
    return new Order(
        priceLevel, externalId.toString(), quantity, remainingQuantity, orderType, isMarketMaker);
  }

  private static Cluster mockCluster() {
    return new Cluster() {
      @Override
      public int memberId() {
        return 0;
      }

      @Override
      public Role role() {
        return null;
      }

      @Override
      public Aeron aeron() {
        return null;
      }

      @Override
      public Context context() {
        return null;
      }

      @Override
      public ClientSession getClientSession(long clusterSessionId) {
        return null;
      }

      @Override
      public Collection<ClientSession> clientSessions() {
        return null;
      }

      @Override
      public boolean closeSession(long clusterSessionId) {
        return false;
      }

      @Override
      public long timeMs() {
        return 0;
      }

      @Override
      public boolean scheduleTimer(long correlationId, long deadlineMs) {
        return false;
      }

      @Override
      public boolean cancelTimer(long correlationId) {
        return false;
      }

      @Override
      public void idle() {
        FifoMatchingEngineTest.IDLE_STRATEGY.idle();
      }

      @Override
      public void idle(int workCount) {
        FifoMatchingEngineTest.IDLE_STRATEGY.idle(workCount);
      }
    };
  }
}
