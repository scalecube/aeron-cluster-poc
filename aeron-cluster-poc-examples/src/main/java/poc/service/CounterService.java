package poc.service;

import io.aeron.cluster.service.ClientSession;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CounterService extends StubService {

  private static final Logger logger = LoggerFactory.getLogger(CounterService.class);
  private int count = 0;

  public void onSessionMessage(
      final ClientSession session,
      final long timestampMs,
      final DirectBuffer buffer,
      final int offset,
      final int length,
      final Header header) {

    logger.info("[RCV]: Current count before request={}", count++);
  }

}
