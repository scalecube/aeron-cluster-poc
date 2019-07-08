package io.scalecube.acpoc.benchmarks;

import io.aeron.Image;
import io.aeron.Publication;
import io.aeron.cluster.codecs.CloseReason;
import io.aeron.cluster.service.ClientSession;
import io.aeron.cluster.service.Cluster;
import io.aeron.cluster.service.Cluster.Role;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BenchmarkClusteredService implements ClusteredService {

  private static final Logger logger = LoggerFactory.getLogger(BenchmarkClusteredService.class);

  private Cluster cluster;

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
    if (cluster.role() == Role.LEADER) {
      // Send response back
      session.offer(buffer, offset, length);
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
  }

  @Override
  public void onRoleChange(Role newRole) {
    logger.info("onRoleChange => memberId: {}, new role: {}", cluster.memberId(), newRole);
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
