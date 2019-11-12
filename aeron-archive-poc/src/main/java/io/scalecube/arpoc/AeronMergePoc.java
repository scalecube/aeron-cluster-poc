package io.scalecube.arpoc;

import static io.aeron.archive.codecs.SourceLocation.REMOTE;
import static io.scalecube.arpoc.Utils.awaitPosition;
import static io.scalecube.arpoc.Utils.awaitRecordingCounterId;

import io.aeron.Aeron;
import io.aeron.ChannelUriStringBuilder;
import io.aeron.CommonContext;
import io.aeron.FragmentAssembler;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.archive.ArchivingMediaDriver;
import io.aeron.archive.client.AeronArchive;
import io.aeron.archive.client.ReplayMerge;
import io.aeron.archive.status.RecordingPos;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.LogBufferDescriptor;
import java.io.File;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.CloseHelper;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.status.CountersReader;

public class AeronMergePoc {

  private static final String MESSAGE_PREFIX = "Message-Prefix-";

  private static final String CONTROL_ENDPOINT = "localhost:43265";
  private static final String RECORDING_ENDPOINT = "localhost:43266";
  private static final String LIVE_ENDPOINT = "localhost:43267";
  private static final String REPLAY_ENDPOINT = "localhost:43268";

  private static final int TERM_LENGTH = LogBufferDescriptor.TERM_MIN_LENGTH;
  private static final int PUBLICATION_TAG = 2;
  private static final int STREAM_ID = 10;
  private static final int FRAGMENT_LIMIT = 10;
  private static final int initialMessageCount = 10;
  private static final double totalMessageCount = 20;

  private static Aeron aeron;
  private static AeronArchive aeronArchive;
  private static ArchivingMediaDriver archivingMediaDriver;
  private static final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
  private static final AtomicInteger received = new AtomicInteger();

  private static final ChannelUriStringBuilder publicationChannel =
      new ChannelUriStringBuilder()
          .media(CommonContext.UDP_MEDIA)
          .tags("1," + PUBLICATION_TAG)
          .controlEndpoint(CONTROL_ENDPOINT)
          .controlMode(CommonContext.MDC_CONTROL_MODE_DYNAMIC)
          .termLength(TERM_LENGTH);

  private static ChannelUriStringBuilder recordingChannel =
      new ChannelUriStringBuilder()
          .media(CommonContext.UDP_MEDIA)
          .endpoint(RECORDING_ENDPOINT)
          .controlEndpoint(CONTROL_ENDPOINT);

  private static ChannelUriStringBuilder subscriptionChannel =
      new ChannelUriStringBuilder()
          .media(CommonContext.UDP_MEDIA)
          .controlMode(CommonContext.MDC_CONTROL_MODE_MANUAL);

  private static final ChannelUriStringBuilder liveDestination =
      new ChannelUriStringBuilder()
          .media(CommonContext.UDP_MEDIA)
          .endpoint(LIVE_ENDPOINT)
          .controlEndpoint(CONTROL_ENDPOINT);

  private static final ChannelUriStringBuilder replayDestination =
      new ChannelUriStringBuilder().media(CommonContext.UDP_MEDIA).endpoint(REPLAY_ENDPOINT);

  private static final ChannelUriStringBuilder replayChannel =
      new ChannelUriStringBuilder()
          .media(CommonContext.UDP_MEDIA)
          .isSessionIdTagged(true)
          .sessionId(PUBLICATION_TAG)
          .endpoint(REPLAY_ENDPOINT);

  public static void main(String[] args) {
    final File archiveDir = Paths.get("target", "aeron", "archive").toFile();

    init(archiveDir);
//        shutdownCloseHook();

    final FragmentHandler fragmentHandler =
        new FragmentAssembler(
            (buffer, offset, length, header) -> {
              System.out.println("receive: " + buffer.getStringWithoutLengthAscii(offset, length));

              received.incrementAndGet();
            });

    try (Publication publication = aeron.addPublication(publicationChannel.build(), STREAM_ID)) {
      final int sessionId = publication.sessionId();
      final String recordingChannelSession = recordingChannel.sessionId(sessionId).build();
      final String subscriptionChannelSession = subscriptionChannel.sessionId(sessionId).build();

      aeronArchive.startRecording(recordingChannelSession, STREAM_ID, REMOTE);

      final CountersReader counters = aeron.countersReader();
      final int counterId = awaitRecordingCounterId(counters, publication.sessionId());
      final long recordingId = RecordingPos.getRecordingId(counters, counterId);

      offerMessages(publication, 0, initialMessageCount, MESSAGE_PREFIX);
      awaitPosition(counters, counterId, publication.position());

      try (Subscription subscription =
              aeron.addSubscription(subscriptionChannelSession, STREAM_ID);
          ReplayMerge replayMerge =
              new ReplayMerge(
                  subscription,
                  aeronArchive,
                  replayChannel.build(),
                  replayDestination.build(),
                  liveDestination.build(),
                  recordingId,
                  0)) {
        for (int i = initialMessageCount; i < totalMessageCount; i++) {
          offer(publication, i, MESSAGE_PREFIX);

          if (0 == replayMerge.poll(fragmentHandler, FRAGMENT_LIMIT)) {
            Thread.yield();
          }
        }

        while (received.get() < totalMessageCount || !replayMerge.isMerged()) {
          if (0 == replayMerge.poll(fragmentHandler, FRAGMENT_LIMIT)) {
            Thread.yield();
          }
        }
      } finally {
        aeronArchive.stopRecording(recordingChannelSession, STREAM_ID);
      }
    }
  }

  private static void offer(final Publication publication, final int index, final String prefix) {
    final String message = prefix + index;
    final int length = buffer.putStringWithoutLengthAscii(0, message);

    while (publication.offer(buffer, 0, length) <= 0) {
      Thread.yield();
    }
  }

  private static void offerMessages(
      final Publication publication, final int startIndex, final int count, final String prefix) {

    for (int i = startIndex; i < (startIndex + count); i++) {
      offer(publication, i, prefix);
    }
  }

  private static void init(File archiveDir) {
    final MediaDriver.Context mediaDriverContext = new MediaDriver.Context();

    archivingMediaDriver =
        ArchivingMediaDriver.launch(
            mediaDriverContext
                .termBufferSparseFile(true)
                .publicationTermBufferLength(TERM_LENGTH)
                .threadingMode(ThreadingMode.SHARED)
                .errorHandler(Throwable::printStackTrace)
                .spiesSimulateConnection(false)
                .dirDeleteOnShutdown(true)
                .dirDeleteOnStart(true),
            new Archive.Context()
                .maxCatalogEntries(128)
                .aeronDirectoryName(mediaDriverContext.aeronDirectoryName())
                .errorHandler(Throwable::printStackTrace)
                .archiveDir(archiveDir)
                .fileSyncLevel(0)
                .threadingMode(ArchiveThreadingMode.SHARED)
                .deleteArchiveOnStart(true));

    aeron =
        Aeron.connect(
            new Aeron.Context()
                .errorHandler(Throwable::printStackTrace)
                .aeronDirectoryName(mediaDriverContext.aeronDirectoryName()));

    aeronArchive =
        AeronArchive.connect(
            new AeronArchive.Context().errorHandler(Throwable::printStackTrace).aeron(aeron));
  }

//    private static void shutdownCloseHook() {
//      Runtime.getRuntime()
//          .addShutdownHook(
//              new Thread(
//                  () -> {
//                    CloseHelper.quietCloseAll(aeronArchive, aeron, archivingMediaDriver);
//                    archivingMediaDriver.archive().context().deleteArchiveDirectory();
//                  }));
//    }
}
