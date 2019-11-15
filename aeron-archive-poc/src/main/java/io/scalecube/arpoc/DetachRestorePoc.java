package io.scalecube.arpoc;

import static io.aeron.archive.codecs.SourceLocation.LOCAL;
import static io.scalecube.arpoc.Utils.awaitPosition;
import static io.scalecube.arpoc.Utils.awaitRecordingCounterId;
import static io.scalecube.arpoc.Utils.offerMessages;
import static io.scalecube.arpoc.Utils.offerToPosition;

import io.aeron.Aeron;
import io.aeron.ChannelUriStringBuilder;
import io.aeron.ConcurrentPublication;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.archive.ArchivingMediaDriver;
import io.aeron.archive.client.AeronArchive;
import io.aeron.archive.codecs.SourceLocation;
import io.aeron.archive.status.RecordingPos;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.LogBufferDescriptor;
import io.aeron.samples.raw.Common;
import java.io.File;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.CloseHelper;
import org.agrona.concurrent.status.CountersReader;

public class DetachRestorePoc {

  private static final int TERM_LENGTH = LogBufferDescriptor.TERM_MIN_LENGTH;
  private static final int SEGMENT_LENGTH = TERM_LENGTH * 2;
  private static final int STREAM_ID = 33;
  private static final int MTU_LENGTH = 1024;

  private static final ChannelUriStringBuilder uriBuilder = new ChannelUriStringBuilder()
      .media("udp")
      .endpoint("localhost:3333")
      .mtu(MTU_LENGTH)
      .termLength(TERM_LENGTH);

  private static Aeron aeron;
  private static AeronArchive aeronArchive;
  private static ArchivingMediaDriver archivingMediaDriver;

  public static void main(String[] args) {
    init();

    detachRestoreScenario();

    shutdown();
  }

  private static void detachRestoreScenario() {
    final AtomicInteger received = new AtomicInteger();
    final FragmentHandler fragmentHandler =
        (buffer, offset, length, header) -> {
          System.out.println("buffer: " + buffer.getStringWithoutLengthAscii(offset, length));
          received.incrementAndGet();
        };

    final String messagePrefix = "Message-Prefix-";
    final long targetPosition = (SEGMENT_LENGTH * 3L) + 1;

    try (Publication publication = aeronArchive.addRecordedPublication(uriBuilder.build(), STREAM_ID))
    {
      final CountersReader counters = aeron.countersReader();
      final int counterId = awaitRecordingCounterId(counters, publication.sessionId());
      final long recordingId = RecordingPos.getRecordingId(counters, counterId);

      offerToPosition(publication, messagePrefix, targetPosition);
      awaitPosition(counters, counterId, publication.position());
      aeronArchive.stopRecording(publication);

      final long startPosition = 0L;
      final long segmentFileBasePosition = AeronArchive.segmentFileBasePosition(
          startPosition, SEGMENT_LENGTH * 2L, TERM_LENGTH, SEGMENT_LENGTH);

      aeronArchive.detachSegments(recordingId, segmentFileBasePosition);

      final long attachSegments = aeronArchive.attachSegments(recordingId);
    }
  }

  private static void init() {
    final File archiveDir = Paths.get("target", "aeron", "archive").toFile();
    final MediaDriver.Context mediaDriverContext = new MediaDriver.Context();

    archivingMediaDriver =
        ArchivingMediaDriver.launch(
            mediaDriverContext
                .termBufferSparseFile(true)
                .publicationTermBufferLength(LogBufferDescriptor.TERM_MIN_LENGTH)
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

  private static void shutdown() {
    CloseHelper.quietCloseAll(aeronArchive, aeron, archivingMediaDriver);
    archivingMediaDriver.archive().context().deleteArchiveDirectory();
  }
}
