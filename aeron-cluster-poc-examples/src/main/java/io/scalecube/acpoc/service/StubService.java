package io.scalecube.acpoc.service;

import io.aeron.Image;
import io.aeron.Publication;
import io.aeron.cluster.codecs.CloseReason;
import io.aeron.cluster.service.ClientSession;
import io.aeron.cluster.service.Cluster;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class StubService implements ClusteredService {

  private static final Logger logger = LoggerFactory.getLogger(StubService.class);
  private Cluster cluster;

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
        "onSessionOpen, timestampMs: {} => sessionId: {}, channel: {}, streamId: {}",
        timestampMs,
        session.id(),
        session.responseChannel(),
        session.responseStreamId());
  }

  @Override
  public void onSessionClose(ClientSession session, long timestampMs, CloseReason closeReason) {
    logger.info(
        "onSessionClose, timestampMs: {} => sessionId: {}, channel: {}, streamId: {}, reason: {}",
        timestampMs,
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
    logger.info(
        "onSessionMessage, timestampMs: {} => sessionId: {}, position: {}, content: {}",
        timestampMs,
        session.id(),
        header.position(),
        buffer.getStringWithoutLengthAscii(offset, length));
  }

  @Override
  public void onTimerEvent(long correlationId, long timestampMs) {
    logger.info("onTimerEvent, timestampMs: {} => correlationId: {}", timestampMs, correlationId);
  }

  @Override
  public void onTakeSnapshot(Publication snapshotPublication) {
    logger.info(
        "onTakeSnapshot => publication: sessionId: {}, channel: {}, streamId: {}, position: {}",
        snapshotPublication.sessionId(),
        snapshotPublication.channel(),
        snapshotPublication.streamId(),
        snapshotPublication.position());
  }

  @Override
  public void onLoadSnapshot(Image snapshotImage) {
    logger.info(
        "onLoadSnapshot => image: sessionId: {}, channel: {}, streamId: {}, position: {}",
        snapshotImage.sessionId(),
        snapshotImage.subscription().channel(),
        snapshotImage.subscription().streamId(),
        snapshotImage.position());
  }

  @Override
  public void onRoleChange(Cluster.Role newRole) {
    logger.info("onRoleChange => new role: {}", newRole);
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
