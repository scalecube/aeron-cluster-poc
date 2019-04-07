package io.scalecube.acpoc.service;

import io.aeron.cluster.service.ClientSession;
import io.aeron.logbuffer.Header;
import io.scalecube.acpoc.Utils;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example of {@link io.aeron.cluster.service.ClusteredService}, simply Echo service.
 */
public class EchoService extends StubService {

  private static final Logger logger = LoggerFactory.getLogger(EchoService.class);

  @Override
  public void onSessionMessage(
      final ClientSession session,
      final long timestampMs,
      final DirectBuffer buffer,
      final int offset,
      final int length,
      final Header header) {

    logger.info("SERVER RCV: {} bytes, sending back.", length);
    while (session.offer(buffer, offset, length) < 0) {
      Utils.checkInterruptedStatus();
      Thread.yield();
    }
  }
}
