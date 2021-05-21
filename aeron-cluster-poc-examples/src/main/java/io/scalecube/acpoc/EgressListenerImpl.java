package io.scalecube.acpoc;

import io.aeron.cluster.client.EgressListener;
import io.aeron.cluster.codecs.EventCode;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class EgressListenerImpl implements EgressListener {

  private static final Logger logger = LoggerFactory.getLogger(EgressListenerImpl.class);

  @Override
  public void onMessage(
      long clusterSessionId,
      long timestamp,
      DirectBuffer buffer,
      int offset,
      int length,
      Header header) {
    logger.info(
        "[onMessage]: timestamp: {}; from clusterSession: {}, position: {}, content: '{}'",
        timestamp,
        clusterSessionId,
        header.position(),
        buffer.getStringWithoutLengthAscii(offset, length));
  }

  @Override
  public void onSessionEvent(
      long correlationId,
      long clusterSessionId,
      long leadershipTermId,
      int leaderMemberId,
      EventCode code,
      String detail) {
    logger.info(
        "[onSessionEvent]: correlationId: {}, clusterSessionId: {}, "
            + "leadershipTermId: {}, leaderMemberId: {}, eventCode: {}, detail: {}",
        correlationId,
        clusterSessionId,
        leadershipTermId,
        leaderMemberId,
        code,
        detail);
  }

  @Override
  public void onNewLeader(
      long clusterSessionId, long leadershipTermId, int leaderMemberId, String ingressEndpoints) {
    logger.info(
        "[newLeader]: clusterSessionId: {}, "
            + "leadershipTermId: {}, leaderMemberId: {}, memberEndpoints: {}",
        clusterSessionId,
        leadershipTermId,
        leaderMemberId,
        ingressEndpoints);
  }
}
