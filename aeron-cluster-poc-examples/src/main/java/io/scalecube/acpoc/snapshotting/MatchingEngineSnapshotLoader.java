package io.scalecube.acpoc.snapshotting;

import io.aeron.Image;
import io.aeron.cluster.client.ClusterException;
import io.aeron.logbuffer.ControlledFragmentHandler;
import io.aeron.logbuffer.Header;
import java.util.HashMap;
import java.util.Map;
import om2.exchange.marketdata.match.fifo.snapshotting.BooleanType;
import om2.exchange.marketdata.match.fifo.snapshotting.MatchingEngineDecoder;
import om2.exchange.marketdata.match.fifo.snapshotting.MessageHeaderDecoder;
import om2.exchange.marketdata.match.fifo.snapshotting.OrderDecoder;
import om2.exchange.marketdata.match.fifo.snapshotting.OrderSide;
import om2.exchange.marketdata.match.fifo.snapshotting.OrderType;
import om2.exchange.marketdata.match.fifo.snapshotting.PriceLevelDecoder;
import om2.exchange.marketdata.match.fifo.snapshotting.SnapshotType;
import org.agrona.DirectBuffer;

public class MatchingEngineSnapshotLoader implements ControlledFragmentHandler {

  private static final int FRAGMENT_LIMIT = 10;

  private final Image image;

  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
  private final MatchingEngineDecoder matchingEngineDecoder = new MatchingEngineDecoder();
  private final PriceLevelDecoder priceLevelDecoder = new PriceLevelDecoder();
  private final OrderDecoder orderDecoder = new OrderDecoder();

  private boolean inSnapshot = false;
  private boolean isDone = false;

  // Matching Engine State
  private String instrumentId;
  private final Map<Long, PriceLevel> bids = new HashMap<>();
  private final Map<Long, PriceLevel> asks = new HashMap<>();
  private PriceLevel currentPriceLevel;

  public MatchingEngineSnapshotLoader(Image image) {
    this.image = image;
  }

  boolean isDone() {
    return isDone;
  }

  int poll() {
    return image.controlledPoll(this, FRAGMENT_LIMIT);
  }

  @Override
  public Action onFragment(DirectBuffer buffer, int offset, int length, Header header) {

    messageHeaderDecoder.wrap(buffer, offset);

    final int schemaId = messageHeaderDecoder.schemaId();
    if (schemaId != MessageHeaderDecoder.SCHEMA_ID) {
      throw new ClusterException(
          "expected schemaId=" + MessageHeaderDecoder.SCHEMA_ID + ", actual=" + schemaId);
    }

    final int templateId = messageHeaderDecoder.templateId();
    switch (templateId) {
      case MatchingEngineDecoder.TEMPLATE_ID:
        matchingEngineDecoder.wrap(
            buffer,
            offset + MessageHeaderDecoder.ENCODED_LENGTH,
            messageHeaderDecoder.blockLength(),
            messageHeaderDecoder.version());

        final SnapshotType typeId = matchingEngineDecoder.snapshot();
        final String instrumentId = matchingEngineDecoder.instrumentId();
        switch (typeId) {
          case START:
            if (inSnapshot) {
              throw new ClusterException("already in snapshot");
            }
            inSnapshot = true;
            // todo
            return Action.CONTINUE;
          case END:
            if (!inSnapshot) {
              throw new ClusterException("missing begin snapshot");
            }
            isDone = true;
            // todo
            return Action.BREAK;
          default:
            throw new ClusterException("unexpected snapshot type: " + typeId);
        }
      case PriceLevelDecoder.TEMPLATE_ID:
        priceLevelDecoder.wrap(
            buffer,
            offset + MessageHeaderDecoder.ENCODED_LENGTH,
            messageHeaderDecoder.blockLength(),
            messageHeaderDecoder.version());

        final long price = priceLevelDecoder.price();
        final OrderSide side = priceLevelDecoder.side();
        // todo
        break;
      case OrderDecoder.TEMPLATE_ID:
        orderDecoder.wrap(
            buffer,
            offset + MessageHeaderDecoder.ENCODED_LENGTH,
            messageHeaderDecoder.blockLength(),
            messageHeaderDecoder.version());

        String externalOrderId = orderDecoder.externalOrderId();
        long originalQuantity = orderDecoder.originalQuantity();
        long remainingQuantity = orderDecoder.remainingQuantity();
        OrderType orderType = orderDecoder.orderType();
        boolean isMarketMaker = orderDecoder.isMarketMaker() == BooleanType.TRUE;
        // todo
        break;
    }

    return ControlledFragmentHandler.Action.CONTINUE;
  }
}
