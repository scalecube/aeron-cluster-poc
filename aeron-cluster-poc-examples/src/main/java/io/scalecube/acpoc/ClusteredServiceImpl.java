package io.scalecube.acpoc;

import io.aeron.Image;
import io.aeron.Publication;
import io.aeron.archive.client.AeronArchive;
import io.aeron.cluster.ClusterControl;
import io.aeron.cluster.ClusterControl.ToggleState;
import io.aeron.cluster.codecs.CloseReason;
import io.aeron.cluster.service.ClientSession;
import io.aeron.cluster.service.Cluster;
import io.aeron.cluster.service.Cluster.Role;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.AtomicCounter;
import org.agrona.concurrent.status.CountersManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClusteredServiceImpl implements ClusteredService {

  private static final Logger logger = LoggerFactory.getLogger(ClusteredServiceImpl.class);

  public static final int TIMER_1_INTERVAL = 3000;
  public static final int TIMER_2_INTERVAL = 5000;
  public static final String TIMER_1_COMMAND = "SCHEDULE_TIMER_1";
  public static final String TIMER_2_COMMAND = "SCHEDULE_TIMER_2";
  public static final String SNAPSHOT_COMMAND = "SNAPSHOT";

  private final CountersManager countersManager;
  private final AeronArchive.Context aeronArchiveContext;

  private Cluster cluster;
  private AeronArchive aeronArchive;

  // State

  private final AtomicInteger serviceCounter = new AtomicInteger();
  private EventRecorder eventRecorder;
  private RecordingDescriptor eventRecording;

  public ClusteredServiceImpl(
      CountersManager countersManager, AeronArchive.Context aeronArchiveContext) {
    this.countersManager = countersManager;
    this.aeronArchiveContext = aeronArchiveContext;
  }

  @Override
  public void onStart(Cluster cluster, Image snapshotImage) {
    this.cluster = cluster;
    logger.info(
        "onStart => memberId: {}, role: {}, client-sessions: {}",
        cluster.memberId(),
        cluster.role(),
        cluster.clientSessions().size());
    if (snapshotImage != null) {
      onLoadSnapshot(snapshotImage);
    }

    aeronArchive = AeronArchive.connect(aeronArchiveContext.clone().idleStrategy(cluster));
    eventRecorder = EventRecorder.createEventRecorder(aeronArchive, cluster, eventRecording);
  }

  @Override
  public void onSessionOpen(ClientSession session, long timestampMs) {
    logger.info(
        "onSessionOpen, timestampMs: {} => memberId: {}, sessionId: {}, "
            + "responseChannel: {}, responseStreamId: {}",
        timestampMs,
        cluster.memberId(),
        session.id(),
        session.responseChannel(),
        session.responseStreamId());
  }

  @Override
  public void onSessionClose(ClientSession session, long timestampMs, CloseReason closeReason) {
    logger.info(
        "onSessionClose, timestampMs: {} => memberId: {}, "
            + "sessionId: {}, responseChannel: {}, responseStreamId: {}, reason: {}",
        timestampMs,
        cluster.memberId(),
        session.id(),
        session.responseChannel(),
        session.responseStreamId(),
        closeReason);
  }

  @Override
  public void onSessionMessage(
      ClientSession session,
      long timestampMs,
      DirectBuffer buffer,
      int offset,
      int length,
      Header header) {
    byte[] bytes = new byte[length];
    buffer.getBytes(offset, bytes);

    String message = new String(bytes);

    logger.info(
        "### onSessionMessage, timestampMs: {} => memberId: {}, "
            + "sessionId: {}, position: {}, content: '{}'",
        new Date(timestampMs),
        cluster.memberId(),
        session,
        header.position(),
        message);

    // Updated service state
    int value = serviceCounter.incrementAndGet();

    // if (TIMER_1_COMMAND.equalsIgnoreCase(message)) {
    //   scheduleTimer(1, cluster.time() + TIMER_1_INTERVAL);
    // }
    // if (TIMER_2_COMMAND.equalsIgnoreCase(message)) {
    //   scheduleTimer(2, cluster.time() + TIMER_2_INTERVAL);
    // }

    if (SNAPSHOT_COMMAND.equalsIgnoreCase(message)) {
      AtomicCounter controlToggle = ClusterControl.findControlToggle(countersManager);
      toggle(controlToggle, ToggleState.SNAPSHOT);
    } else {
      eventRecorder.recordEvent(bytes);
    }

    if (session != null) {
      if (cluster.role() == Role.LEADER) {
        // Send response back
        String response = message + ", ClusteredService.serviceCounter(value=" + value + ")";
        UnsafeBuffer buffer1 = new UnsafeBuffer(response.getBytes());
        long l = session.offer(buffer1, 0, buffer1.capacity());
        if (l > 0) {
          logger.info("Service: RESPONSE send result={}, serviceCounter(value={})", l, value);
        }
      }
    }
  }

  @Override
  public void onTimerEvent(long correlationId, long timestampMs) {
    logger.info(
        "*** onTimerEvent timestampMs: {} => memberId: {}, correlationId: {}",
        new Date(timestampMs),
        cluster.memberId(),
        correlationId);

    // if (correlationId == 1) {
    //   scheduleTimer(correlationId, cluster.time() + TIMER_1_INTERVAL);
    // }
    // if (correlationId == 2) {
    //   scheduleTimer(correlationId, cluster.time() + TIMER_2_INTERVAL);
    // }
  }

  @Override
  public void onTakeSnapshot(Publication snapshotPublication) {
    logger.info(
        "onTakeSnapshot => publication: memberId: {}, sessionId: {}, channel: {}, "
            + "streamId: {}, position: {}",
        cluster.memberId(),
        snapshotPublication.sessionId(),
        snapshotPublication.channel(),
        snapshotPublication.streamId(),
        snapshotPublication.position());

    eventRecording = eventRecorder.eventRecording(aeronArchive);

    final Map<String, String> map = new HashMap<>();
    map.put("serviceCounter", String.valueOf(serviceCounter.get()));
    map.put("eventRecording.recordingId", String.valueOf(eventRecording.recordingId));
    map.put("eventRecording.initialTermId", String.valueOf(eventRecording.initialTermId));
    map.put("eventRecording.termBufferLength", String.valueOf(eventRecording.termBufferLength));
    map.put("eventRecording.segmentFileLength", String.valueOf(eventRecording.segmentFileLength));
    map.put("eventRecording.startPosition", String.valueOf(eventRecording.startPosition));
    map.put("eventRecording.stopPosition", String.valueOf(eventRecording.stopPosition));
    map.put("eventRecording.mtuLength", String.valueOf(eventRecording.mtuLength));
    map.put("eventRecording.recordingPosition", String.valueOf(eventRecording.recordingPosition));

    final byte[] bytes = Utils.toJson(map);
    long offer = snapshotPublication.offer(new UnsafeBuffer(bytes));

    logger.info(
        "onTakeSnapshot => memberId: {}, ({}) snapshot taken: {}", cluster.memberId(), map, offer);
  }

  private void onLoadSnapshot(Image snapshotImage) {
    logger.info(
        "onLoadSnapshot => image: memberId: {}, sessionId: {}, channel: {}, "
            + "streamId: {}, position: {}",
        cluster.memberId(),
        snapshotImage.sessionId(),
        snapshotImage.subscription().channel(),
        snapshotImage.subscription().streamId(),
        snapshotImage.position());

    FragmentHandler handler =
        (buffer, offset, length, header) -> {
          Map<String, String> map = Utils.fromJson(buffer, offset, length);
          serviceCounter.set(Integer.parseInt(map.get("serviceCounter")));
          eventRecording = new RecordingDescriptor();
          eventRecording.recordingId = Long.parseLong( map.get("eventRecording.recordingId"));
          eventRecording.initialTermId = Integer.parseInt(map.get("eventRecording.initialTermId"));
          eventRecording.termBufferLength = Integer.parseInt(map.get("eventRecording.termBufferLength"));
          eventRecording.segmentFileLength = Integer.parseInt(map.get("eventRecording.segmentFileLength"));
          eventRecording.startPosition = Long.parseLong( map.get("eventRecording.startPosition"));
          eventRecording.stopPosition = Long.parseLong( map.get("eventRecording.stopPosition"));
          eventRecording.mtuLength = Integer.parseInt( map.get("eventRecording.mtuLength"));
          eventRecording.recordingPosition =Long.parseLong( map.get("eventRecording.recordingPosition"));
        };

    while (true) {
      int fragments = snapshotImage.poll(handler, 1);

      if (fragments == 1) {
        break;
      }
      cluster.idle();
      System.out.print(".");
    }

    logger.info(
        "onLoadSnapshot => memberId: {}, applied new serviceCounter(value={}, eventRecording={})",
        cluster.memberId(),
        serviceCounter.get(),
        eventRecording);
  }

  @Override
  public void onRoleChange(Cluster.Role newRole) {
    logger.info(
        "onRoleChange => memberId: {}, new role: {}, timestampMs: {}",
        cluster.memberId(),
        newRole,
        new Date(cluster.time()));

    // if (newRole == Role.LEADER) {
    //   sendTimerCommand();
    // }
  }

  @Override
  public void onTerminate(Cluster cluster) {
    logger.info(
        "onTerminate => memberId: {}, role: {}, client-sessions: {}",
        cluster.memberId(),
        cluster.role(),
        cluster.clientSessions().size());
  }

  private void toggle(AtomicCounter controlToggle, ToggleState target) {
    ToggleState oldToggleState = ToggleState.get(controlToggle);
    boolean result = target.toggle(controlToggle);
    ToggleState newToggleState = ToggleState.get(controlToggle);
    logger.info(
        "ToggleState changed {}: {}->{}",
        result ? "succesfuly" : "unsuccesfuly",
        oldToggleState,
        newToggleState);
  }

  private void sendTimerCommand() {
    UnsafeBuffer buffer1 = new UnsafeBuffer(TIMER_1_COMMAND.getBytes());
    long l = cluster.offer(buffer1, 0, buffer1.capacity());
    if (l > 0) {
      logger.info("Timer1Command: send result={}", l);
    }

    UnsafeBuffer buffer2 = new UnsafeBuffer(TIMER_2_COMMAND.getBytes());
    long l2 = cluster.offer(buffer2, 0, buffer2.capacity());
    if (l2 > 0) {
      logger.info("Timer2Command: send result={}", l2);
    }
  }

  private void scheduleTimer(long correlationId, long deadlineMs) {
    boolean scheduleTimer = cluster.scheduleTimer(correlationId, deadlineMs);
    if (scheduleTimer) {
      logger.info("Timer ({}) scheduled at {}", correlationId, new Date(deadlineMs));
    }
  }
}
