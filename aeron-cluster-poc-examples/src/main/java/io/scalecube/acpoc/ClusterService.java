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
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.AtomicCounter;
import org.agrona.concurrent.status.CountersManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

public class ClusterService implements ClusteredService {

  private static final Logger logger = LoggerFactory.getLogger(ClusterService.class);

  private static final Duration SNAPSHOT_PERIOD = Duration.ofSeconds(20);

  private final CountersManager countersManager;

  private Cluster cluster;

  // State

  private final AtomicInteger serviceCounter = new AtomicInteger();

  private Disposable snapshotDisposable;

  public ClusterService(CountersManager countersManager) {
    this.countersManager = countersManager;
  }

  @Override
  public void onStart(Cluster cluster) {
    this.cluster = cluster;
    logger.info(
        "onStart => memberId: {}, role: {}, client-sessions: {}",
        cluster.memberId(),
        cluster.role(),
        cluster.clientSessions().size());
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

    logger.info(
        "onSessionMessage, timestampMs: {} => memberId: {}, "
            + "sessionId: {}, position: {}, content: {}",
        timestampMs,
        cluster.memberId(),
        session.id(),
        header.position(),
        new String(bytes));

    // Updated service state
    serviceCounter.incrementAndGet();

    // Send response back
    while (session.offer(buffer, offset, length) < 0) {
      Utils.checkInterruptedStatus();
      Thread.yield();
    }
  }

  @Override
  public void onTimerEvent(long correlationId, long timestampMs) {
    logger.info(
        "onTimerEvent, timestampMs: {} => memberId: {}, correlationId: {}",
        timestampMs,
        cluster.memberId(),
        correlationId);
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
    buffer.putInt(0, serviceCounter.get());
    long offer = snapshotPublication.offer(buffer);

    logger.info(
        "onTakeSnapshot => memberId: {}, serviceCounter snapshot taken: {}",
        cluster.memberId(),
        offer);
  }

  @Override
  public void onLoadSnapshot(Image snapshotImage) {
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
    }

    logger.info(
        "onLoadSnapshot => memberId: {}, applied new serviceCounter : {}",
        cluster.memberId(),
        serviceCounter.get());
  }

  @Override
  public void onRoleChange(Cluster.Role newRole) {
    logger.info("onRoleChange => memberId: {}, new role: {}", cluster.memberId(), newRole);
    // Schedule process of taking snapshot if on leader
    if (newRole == Role.LEADER) {
      AtomicCounter controlToggle = ClusterControl.findControlToggle(countersManager);
      if (snapshotDisposable != null) {
        snapshotDisposable.dispose();
      }
      snapshotDisposable =
          Flux.interval(SNAPSHOT_PERIOD)
              .subscribe(
                  i -> {
                    boolean result = ToggleState.SNAPSHOT.toggle(controlToggle);
                    logger.info("ToggleState to SNAPSHOT: " + result);
                  },
                  System.err::println);
    } else {
      if (snapshotDisposable != null) {
        snapshotDisposable.dispose();
      }
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
}
