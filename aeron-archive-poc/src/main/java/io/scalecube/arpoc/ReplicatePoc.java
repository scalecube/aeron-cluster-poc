package io.scalecube.arpoc;

import static io.aeron.Aeron.NULL_VALUE;
import static io.aeron.archive.codecs.SourceLocation.LOCAL;
import static io.scalecube.arpoc.Utils.awaitPosition;
import static io.scalecube.arpoc.Utils.awaitRecordingCounterId;
import static io.scalecube.arpoc.Utils.awaitSignal;
import static io.scalecube.arpoc.Utils.offerMessages;

import io.aeron.Aeron;
import io.aeron.ChannelUriStringBuilder;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.archive.ArchivingMediaDriver;
import io.aeron.archive.client.AeronArchive;
import io.aeron.archive.client.ControlEventListener;
import io.aeron.archive.client.RecordingSignalAdapter;
import io.aeron.archive.client.RecordingSignalConsumer;
import io.aeron.archive.codecs.ControlResponseCode;
import io.aeron.archive.codecs.RecordingSignal;
import io.aeron.archive.status.RecordingPos;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import io.aeron.logbuffer.LogBufferDescriptor;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.SystemUtil;
import org.agrona.collections.MutableLong;
import org.agrona.collections.MutableReference;
import org.agrona.concurrent.YieldingIdleStrategy;
import org.agrona.concurrent.status.CountersReader;

public class ReplicatePoc {

  private static final int SRC_CONTROL_STREAM_ID =
      AeronArchive.Configuration.CONTROL_STREAM_ID_DEFAULT;
  private static final String SRC_CONTROL_REQUEST_CHANNEL = "aeron:udp?endpoint=localhost:8090";
  private static final String DST_CONTROL_REQUEST_CHANNEL = "aeron:udp?endpoint=localhost:8091";
  private static final String SRC_CONTROL_RESPONSE_CHANNEL = "aeron:udp?endpoint=localhost:8095";
  private static final String DST_CONTROL_RESPONSE_CHANNEL = "aeron:udp?endpoint=localhost:8096";
  private static final String SRC_REPLICATION_CHANNEL = "aeron:udp?endpoint=localhost:8040";
  private static final String DST_REPLICATION_CHANNEL = "aeron:udp?endpoint=localhost:8041";

  private static final int LIVE_STREAM_ID = 33;
  private static final String LIVE_CHANNEL =
      new ChannelUriStringBuilder()
          .media("udp")
          .endpoint("224.20.30.39:54326")
          .networkInterface("localhost")
          .termLength(LogBufferDescriptor.TERM_MIN_LENGTH)
          .build();

  private static ArchivingMediaDriver srcArchivingMediaDriver;
  private static ArchivingMediaDriver dstArchivingMediaDriver;
  private static Aeron srcAeron;
  private static Aeron dstAeron;
  private static AeronArchive srcAeronArchive;
  private static AeronArchive dstAeronArchive;

  public static void main(String[] args) {
    init();

    replicateScenario();

    shutdown();
  }

  private static void replicateScenario() {
    final AtomicInteger received = new AtomicInteger();
    final FragmentHandler fragmentHandler =
        (buffer, offset, length, header) -> {
          System.out.println("buffer: " + buffer.getStringWithoutLengthAscii(offset, length));
          received.incrementAndGet();
        };

    final String messagePrefix = "Message-Prefix-";
    final int messageCount = 10;
    final long srcRecordingId;

    final long subscriptionId = srcAeronArchive.startRecording(LIVE_CHANNEL, LIVE_STREAM_ID, LOCAL);
    final MutableReference<RecordingSignal> signalRef = new MutableReference<>();
    final RecordingSignalAdapter adapter;

    try (final Publication publication = srcAeron.addPublication(LIVE_CHANNEL, LIVE_STREAM_ID)) {

      final CountersReader srcCounters = srcAeron.countersReader();
      final int counterId = awaitRecordingCounterId(srcCounters, publication.sessionId());
      srcRecordingId = RecordingPos.getRecordingId(srcCounters, counterId);

      offerMessages(publication, 0, messageCount, messagePrefix);
      awaitPosition(srcCounters, counterId, publication.position());

      final MutableLong dstRecordingId = new MutableLong();
      adapter = newRecordingSignalAdapter(signalRef, dstRecordingId);

      dstAeronArchive.replicate(
          srcRecordingId,
          NULL_VALUE,
          SRC_CONTROL_STREAM_ID,
          SRC_CONTROL_REQUEST_CHANNEL,
          LIVE_CHANNEL);

      offerMessages(publication, messageCount, messageCount, messagePrefix);

      awaitSignal(signalRef, adapter);
      awaitSignal(signalRef, adapter);
      awaitSignal(signalRef, adapter);

      final CountersReader dstCounters = dstAeron.countersReader();
      final int dstCounterId =
          RecordingPos.findCounterIdByRecording(dstCounters, dstRecordingId.get());

      offerMessages(publication, 2 * messageCount, messageCount, messagePrefix);
      awaitPosition(dstCounters, dstCounterId, publication.position());

      dstAeronArchive.startReplay(srcRecordingId, 0, -1, "aeron:udp?endpoint=localhost:8140", 88);

      final Subscription subscription =
          dstAeron.addSubscription("aeron:udp?endpoint=localhost:8140", 88);

      while (received.get() < messageCount * 3) {
        subscription.poll(fragmentHandler, 10);
      }
    }

    srcAeronArchive.stopRecording(subscriptionId);

    awaitSignal(signalRef, adapter);
  }

  private static RecordingSignalAdapter newRecordingSignalAdapter(
      final MutableReference<RecordingSignal> signalRef, final MutableLong recordingIdRef) {
    final ControlEventListener listener =
        (controlSessionId, correlationId, relevantId, code, errorMessage) -> {
          if (code == ControlResponseCode.ERROR) {
            System.out.println("ERROR: errorMessage" + " " + code);
          }
        };

    final RecordingSignalConsumer consumer =
        (controlSessionId,
            correlationId,
            recordingId,
            subscriptionId,
            position,
            transitionType) -> {
          recordingIdRef.set(recordingId);
          signalRef.set(transitionType);
        };

    final Subscription subscription = dstAeronArchive.controlResponsePoller().subscription();
    final long controlSessionId = dstAeronArchive.controlSessionId();

    return new RecordingSignalAdapter(controlSessionId, listener, consumer, subscription, 10);
  }

  private static void init() {
    final Path srcAeronDir = Paths.get("target", "aeron", "archive1");
    final String srcAeronDirectoryName = srcAeronDir.toString();
    final Path dstAeronDir = Paths.get("target", "aeron", "archive2");
    final String dstAeronDirectoryName = dstAeronDir.toString();

    srcArchivingMediaDriver =
        ArchivingMediaDriver.launch(
            new MediaDriver.Context()
                .aeronDirectoryName(srcAeronDirectoryName)
                .termBufferSparseFile(true)
                .threadingMode(ThreadingMode.SHARED)
                .errorHandler(Throwable::printStackTrace)
                .spiesSimulateConnection(true)
                .dirDeleteOnShutdown(false)
                .dirDeleteOnStart(true),
            new Archive.Context()
                .maxCatalogEntries(128)
                .aeronDirectoryName(srcAeronDirectoryName)
                .controlChannel(SRC_CONTROL_REQUEST_CHANNEL)
                .archiveClientContext(
                    new AeronArchive.Context().controlResponseChannel(SRC_CONTROL_RESPONSE_CHANNEL))
                .recordingEventsEnabled(false)
                .replicationChannel(SRC_REPLICATION_CHANNEL)
                .deleteArchiveOnStart(true)
                .archiveDir(new File(srcAeronDir.toFile(), "src-archive"))
                .fileSyncLevel(0)
                .threadingMode(ArchiveThreadingMode.SHARED));

    dstArchivingMediaDriver =
        ArchivingMediaDriver.launch(
            new MediaDriver.Context()
                .aeronDirectoryName(dstAeronDirectoryName)
                .termBufferSparseFile(true)
                .threadingMode(ThreadingMode.SHARED)
                .errorHandler(Throwable::printStackTrace)
                .spiesSimulateConnection(true)
                .dirDeleteOnShutdown(false)
                .dirDeleteOnStart(true),
            new Archive.Context()
                .maxCatalogEntries(128)
                .aeronDirectoryName(dstAeronDirectoryName)
                .controlChannel(DST_CONTROL_REQUEST_CHANNEL)
                .archiveClientContext(
                    new AeronArchive.Context().controlResponseChannel(DST_CONTROL_RESPONSE_CHANNEL))
                .recordingEventsEnabled(false)
                .replicationChannel(DST_REPLICATION_CHANNEL)
                .deleteArchiveOnStart(true)
                .archiveDir(new File(dstAeronDir.toFile(), "dst-archive"))
                .fileSyncLevel(0)
                .threadingMode(ArchiveThreadingMode.SHARED));

    srcAeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(srcAeronDirectoryName));

    dstAeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(dstAeronDirectoryName));

    srcAeronArchive =
        AeronArchive.connect(
            new AeronArchive.Context()
                .idleStrategy(YieldingIdleStrategy.INSTANCE)
                .controlRequestChannel(SRC_CONTROL_REQUEST_CHANNEL)
                .aeron(dstAeron));

    dstAeronArchive =
        AeronArchive.connect(
            new AeronArchive.Context()
                .idleStrategy(YieldingIdleStrategy.INSTANCE)
                .controlRequestChannel(DST_CONTROL_REQUEST_CHANNEL)
                .aeron(dstAeron));
  }

  private static void shutdown() {
    CloseHelper.close(srcAeronArchive);
    CloseHelper.close(dstAeronArchive);
    CloseHelper.close(srcAeron);
    CloseHelper.close(dstAeron);
    CloseHelper.close(dstArchivingMediaDriver);
    CloseHelper.close(srcArchivingMediaDriver);

    dstArchivingMediaDriver.archive().context().deleteArchiveDirectory();
    srcArchivingMediaDriver.archive().context().deleteArchiveDirectory();
  }
}
