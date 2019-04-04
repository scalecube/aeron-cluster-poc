package aeron.cluster.poc.service;

import aeron.cluster.poc.Utils;
import io.aeron.cluster.service.ClientSession;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EchoService extends StubService {

  private static final Logger logger = LoggerFactory.getLogger(EchoService.class);

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
