package io.scalecube.acpoc.snapshotting;

import io.aeron.Publication;
import io.aeron.cluster.service.Cluster;
import io.aeron.exceptions.AeronException;
import io.aeron.logbuffer.BufferClaim;
import java.util.Map;
import om2.exchange.marketdata.match.fifo.snapshotting.BooleanType;
import om2.exchange.marketdata.match.fifo.snapshotting.MatchingEngineEncoder;
import om2.exchange.marketdata.match.fifo.snapshotting.MessageHeaderEncoder;
import om2.exchange.marketdata.match.fifo.snapshotting.OrderEncoder;
import om2.exchange.marketdata.match.fifo.snapshotting.PriceLevelEncoder;

public class FifoMatchingEngine {

  private final String instrumentId;

  private final Map<Long, PriceLevel> bids;
  private final Map<Long, PriceLevel> asks;

  private final FifoMatchingEngineSnapshotTaker snapshotTaker =
      new FifoMatchingEngineSnapshotTaker();

  /** Creator. */
  public FifoMatchingEngine(
      String instrumentId, Map<Long, PriceLevel> bids, Map<Long, PriceLevel> asks) {
    this.bids = bids;
    this.asks = asks;
    this.instrumentId = instrumentId;
  }

  public void takeSnapshot(Cluster cluster, Publication publication) {
    snapshotTaker.snapshotMatchingEngine(cluster, publication);
  }

  private class FifoMatchingEngineSnapshotTaker {

    private final BufferClaim bufferClaim = new BufferClaim();
    private final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    private final MatchingEngineEncoder matchingEngineEncoder = new MatchingEngineEncoder();
    private final PriceLevelEncoder priceLevelEncoder = new PriceLevelEncoder();
    private final OrderEncoder orderEncoder = new OrderEncoder();

    private void snapshotMatchingEngine(Cluster cluster, Publication publication) {
      storeMatchingEngineInfo(cluster, publication);
      for (PriceLevel priceLevel : bids.values()) {
        storePriceLevel(cluster, publication, priceLevel);
      }
      for (PriceLevel priceLevel : asks.values()) {
        storePriceLevel(cluster, publication, priceLevel);
      }
      markEnd(cluster, publication);
    }

    private void storeMatchingEngineInfo(Cluster cluster, Publication publication) {
      int length = MessageHeaderEncoder.ENCODED_LENGTH + MatchingEngineEncoder.BLOCK_LENGTH;
      while (true) {
        final long result = publication.tryClaim(length, bufferClaim);

        if (result > 0) {
          matchingEngineEncoder
              .wrapAndApplyHeader(bufferClaim.buffer(), bufferClaim.offset(), messageHeaderEncoder)
              .instrumentId(instrumentId);
          bufferClaim.commit();
          break;
        }
        checkResult(result);
        cluster.idle();
      }
    }

    private void storePriceLevel(Cluster cluster, Publication publication, PriceLevel priceLevel) {
      int length = MessageHeaderEncoder.ENCODED_LENGTH + PriceLevelEncoder.BLOCK_LENGTH;
      while (true) {
        final long result = publication.tryClaim(length, bufferClaim);

        if (result > 0) {
          priceLevelEncoder
              .wrapAndApplyHeader(bufferClaim.buffer(), bufferClaim.offset(), messageHeaderEncoder)
              .side(priceLevel.side)
              .price(priceLevel.price);
          bufferClaim.commit();
          break;
        }
        checkResult(result);
        cluster.idle();
      }
      storeOrders(cluster, publication, priceLevel);
    }

    private void storeOrders(Cluster cluster, Publication publication, PriceLevel priceLevel) {
      int length = MessageHeaderEncoder.ENCODED_LENGTH + OrderEncoder.BLOCK_LENGTH;
      for (Order order : priceLevel.orders) {
        while (true) {
          final long result = publication.tryClaim(length, bufferClaim);

          if (result > 0) {
            orderEncoder
                .wrapAndApplyHeader(
                    bufferClaim.buffer(), bufferClaim.offset(), messageHeaderEncoder)
                .externalOrderId(order.externalOrderId)
                .originalQuantity(order.originalQuantity)
                .remainingQuantity(order.remainingQuantity)
                .orderType(order.orderType)
                .isMarketMaker(order.isMarketMaker ? BooleanType.TRUE : BooleanType.FALSE);
            bufferClaim.commit();
            break;
          }
          checkResult(result);
          cluster.idle();
        }
      }
    }

    private void markEnd(Cluster cluster, Publication publication) {
      int length = MessageHeaderEncoder.ENCODED_LENGTH + MatchingEngineEncoder.BLOCK_LENGTH;
      while (true) {
        final long result = publication.tryClaim(length, bufferClaim);

        if (result > 0) {
          matchingEngineEncoder
              .wrapAndApplyHeader(bufferClaim.buffer(), bufferClaim.offset(), messageHeaderEncoder)
              .instrumentId(instrumentId);
          bufferClaim.commit();
          break;
        }
        checkResult(result);
        cluster.idle();
      }
    }

    private void checkResult(final long result) {
      if (result == Publication.NOT_CONNECTED
          || result == Publication.CLOSED
          || result == Publication.MAX_POSITION_EXCEEDED) {
        throw new AeronException("unexpected publication state: " + result);
      }
    }
  }
}
