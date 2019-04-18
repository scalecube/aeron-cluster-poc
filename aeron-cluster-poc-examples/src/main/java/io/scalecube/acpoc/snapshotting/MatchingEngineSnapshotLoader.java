package io.scalecube.acpoc.snapshotting;

import io.aeron.Image;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MatchingEngineSnapshotLoader implements ControlledFragmentHandler {

  private static final Logger logger = LoggerFactory.getLogger(MatchingEngineSnapshotLoader.class);

  private static final int FRAGMENT_LIMIT = 10;

  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
  private final MatchingEngineDecoder matchingEngineDecoder = new MatchingEngineDecoder();
  private final PriceLevelDecoder priceLevelDecoder = new PriceLevelDecoder();
  private final OrderDecoder orderDecoder = new OrderDecoder();

  private final Image image;

  private boolean inSnapshot = false;
  private boolean isDone = false;

  // Matching Engine State
  private String instrumentId;
  private Map<Long, PriceLevel> bids;
  private Map<Long, PriceLevel> asks;
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

  FifoMatchingEngine matchingEngine() {
    return new FifoMatchingEngine(instrumentId, bids, asks);
  }

  @Override
  public Action onFragment(DirectBuffer buffer, int offset, int length, Header header) {
    messageHeaderDecoder.wrap(buffer, offset);

    final int schemaId = messageHeaderDecoder.schemaId();
    if (schemaId != MessageHeaderDecoder.SCHEMA_ID) {
      throw new RuntimeException(
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
              throw new RuntimeException("already in snapshot");
            }
            inSnapshot = true;
            this.instrumentId = instrumentId;
            this.bids = new HashMap<>();
            this.asks = new HashMap<>();
            this.currentPriceLevel = null;

            logger.info("Started reading matching engine for instrumentId: {}", instrumentId);
            return Action.CONTINUE;
          case END:
            if (!inSnapshot) {
              throw new RuntimeException("missing begin snapshot");
            }
            isDone = true;

            logger.info("Finished reading matching engine for instrumentId: {}", instrumentId);
            return Action.BREAK;
          default:
            throw new RuntimeException("unexpected snapshot type: " + typeId);
        }

      case PriceLevelDecoder.TEMPLATE_ID:
        priceLevelDecoder.wrap(
            buffer,
            offset + MessageHeaderDecoder.ENCODED_LENGTH,
            messageHeaderDecoder.blockLength(),
            messageHeaderDecoder.version());

        final long price = priceLevelDecoder.price();
        final OrderSide side = priceLevelDecoder.side();
        PriceLevel priceLevel = new PriceLevel(side, price);

        if (side == OrderSide.Buy) {
          bids.put(price, priceLevel);
        } else {
          asks.put(price, priceLevel);
        }
        this.currentPriceLevel = priceLevel;

        logger.info(
            "instrumentId: {}, started reading price level {}",
            this.instrumentId,
            this.currentPriceLevel);
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
        Order order =
            new Order(
                this.currentPriceLevel,
                externalOrderId,
                originalQuantity,
                remainingQuantity,
                orderType,
                isMarketMaker);
        this.currentPriceLevel.add(order);

        logger.info(
            "instrumentId: {}, priceLevel: {}, started reading order {}",
            this.instrumentId,
            this.currentPriceLevel,
            order);
        break;
      default:
        throw new IllegalStateException("unknown templateId: " + templateId);
    }

    return ControlledFragmentHandler.Action.CONTINUE;
  }
}
