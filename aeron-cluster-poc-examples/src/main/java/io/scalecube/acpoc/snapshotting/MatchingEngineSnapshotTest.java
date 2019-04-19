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
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import java.io.File;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.agrona.IoUtil;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.YieldingIdleStrategy;
import org.agrona.concurrent.status.CountersReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MatchingEngineSnapshotTest {

  private static final Logger logger = LoggerFactory.getLogger(MatchingEngineSnapshotLoader.class);

  private static final Random RANDOM = new Random();
  private static final int ORDERS_COUNT = RANDOM.nextInt(100);
  private static final IdleStrategy IDLE_STRATEGY = new YieldingIdleStrategy();

  /** Starter. */
  public static void main(String[] args) {
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

    String nodeDirName = Paths.get(IoUtil.tmpDirName(), "aeron", "test").toString();
    IoUtil.delete(new File(nodeDirName), true);
    logger.info("node directory: {}", nodeDirName);

    String instrumentId = UUID.randomUUID().toString();
    MatchingEngine engine = new MatchingEngine(instrumentId, bids, asks);
    logger.info("before: {}", engine);

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
        logger.debug("recordingId = {}", recordingId);
        engine.takeSnapshot(IDLE_STRATEGY, publication);

        awaitRecordingComplete(aeronArchive, publication, counters, counterId, recordingId);
      } finally {
        aeronArchive.stopRecording(subscriptionId);
      }

      final int replaySessionId =
          (int)
              aeronArchive.startReplay(
                  recordingId, 0, NULL_VALUE, snapshotChannel, snapshotStreamId);
      final String replaySessionChannel = ChannelUri.addSessionId(snapshotChannel, replaySessionId);

      try (Subscription subscription =
          aeron.addSubscription(
              replaySessionChannel,
              snapshotStreamId,
              image -> logger.debug("sessionId: {}, image available", image.sessionId()),
              image -> logger.debug("sessionId: {}, image unavailable", image.sessionId()))) {
        Image image = awaitImage(replaySessionId, subscription);
        MatchingEngine matchingEngine = awaitMatchingEngine(image);
        logger.info("after:  {}", matchingEngine);
      }
    }
  }

  private static MatchingEngine awaitMatchingEngine(Image image) {

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
}
