package io.scalecube.acpoc;

import io.aeron.Image;
import io.aeron.Publication;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.AtomicCounter;
import org.agrona.concurrent.status.CountersManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClusteredServiceImpl implements ClusteredService {

  private static final Logger logger = LoggerFactory.getLogger(ClusteredServiceImpl.class);

  public static final int TIMER_INTERVAL = 3000;
  public static final String TIMER_COMMAND = "SCHEDULE_TIMER";
  public static final String SNAPSHOT_COMMAND = "SNAPSHOT";

  private final CountersManager countersManager;

  private Cluster cluster;

  // State

  private final AtomicInteger serviceCounter = new AtomicInteger();

  public ClusteredServiceImpl(CountersManager countersManager) {
    this.countersManager = countersManager;
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

    if (TIMER_COMMAND.equalsIgnoreCase(message)) {
      if (cluster.role() == Role.LEADER) {
        scheduleTimer();
      }
    }

    if (SNAPSHOT_COMMAND.equalsIgnoreCase(message)) {
      AtomicCounter controlToggle = ClusterControl.findControlToggle(countersManager);
      toggle(controlToggle, ToggleState.SNAPSHOT);
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

    if (cluster.role() == Role.LEADER) {
      scheduleTimer();
    }
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

    UnsafeBuffer buffer = new UnsafeBuffer(new byte[Integer.BYTES]);
    int value = serviceCounter.get();
    buffer.putInt(0, value);
    long offer = snapshotPublication.offer(buffer);

    logger.info(
        "onTakeSnapshot => memberId: {}, serviceCounter(value={}) snapshot taken: {}",
        cluster.memberId(),
        value,
        offer);
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
        (buffer, offset, length, header) -> serviceCounter.set(buffer.getInt(offset));

    while (true) {
      int fragments = snapshotImage.poll(handler, 1);

      if (fragments == 1) {
        break;
      }
      cluster.idle();
      System.out.print(".");
    }

    logger.info(
        "onLoadSnapshot => memberId: {}, applied new serviceCounter(value={})",
        cluster.memberId(),
        serviceCounter.get());
  }

  @Override
  public void onRoleChange(Cluster.Role newRole) {
    logger.info(
        "onRoleChange => memberId: {}, new role: {}, timestampMs: {}",
        cluster.memberId(),
        newRole,
        new Date(cluster.time()));

    if (newRole == Role.LEADER) {
      sendTimerCommand();
    }
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
    UnsafeBuffer buffer1 = new UnsafeBuffer(TIMER_COMMAND.getBytes());
    long l = cluster.offer(buffer1, 0, buffer1.capacity());
    if (l > 0) {
      logger.info("TimerCommand: send result={}", l);
    }
  }

  private void scheduleTimer() {
    long deadlineMs = cluster.time() + TIMER_INTERVAL;
    long correlationId = cluster.time();
    boolean scheduleTimer = cluster.scheduleTimer(correlationId, deadlineMs);
    if (scheduleTimer) {
      logger.info("Timer ({}) scheduled at {}", correlationId, new Date(deadlineMs));
    }
  }
}
