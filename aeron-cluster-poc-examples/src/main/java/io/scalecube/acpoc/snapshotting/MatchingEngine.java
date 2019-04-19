package io.scalecube.acpoc.snapshotting;

import io.aeron.Publication;
import io.aeron.exceptions.AeronException;
import io.aeron.logbuffer.BufferClaim;
import java.util.Map;
import org.agrona.concurrent.IdleStrategy;

public class MatchingEngine {

  private final String instrumentId;

  private final Map<Long, PriceLevel> bids;
  private final Map<Long, PriceLevel> asks;

  private final FifoMatchingEngineSnapshotTaker snapshotTaker =
      new FifoMatchingEngineSnapshotTaker();

  /** Creator. */
  public MatchingEngine(
      String instrumentId, Map<Long, PriceLevel> bids, Map<Long, PriceLevel> asks) {
    this.bids = bids;
    this.asks = asks;
    this.instrumentId = instrumentId;
  }

  public void takeSnapshot(IdleStrategy idleStrategy, Publication publication) {
    snapshotTaker.snapshotMatchingEngine(idleStrategy, publication);
  }

  private class FifoMatchingEngineSnapshotTaker {

    private final BufferClaim bufferClaim = new BufferClaim();
    private final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    private final MatchingEngineEncoder matchingEngineEncoder = new MatchingEngineEncoder();
    private final PriceLevelEncoder priceLevelEncoder = new PriceLevelEncoder();
    private final OrderEncoder orderEncoder = new OrderEncoder();

    private void snapshotMatchingEngine(IdleStrategy idleStrategy, Publication publication) {
      storeMatchingEngineInfo(idleStrategy, publication);
      for (PriceLevel priceLevel : bids.values()) {
        storePriceLevel(idleStrategy, publication, priceLevel);
      }
      for (PriceLevel priceLevel : asks.values()) {
        storePriceLevel(idleStrategy, publication, priceLevel);
      }
      markEnd(idleStrategy, publication);
    }

    private void storeMatchingEngineInfo(IdleStrategy idleStrategy, Publication publication) {
      int length = MessageHeaderEncoder.ENCODED_LENGTH + MatchingEngineEncoder.BLOCK_LENGTH;
      while (true) {
        final long result = publication.tryClaim(length, bufferClaim);

        if (result > 0) {
          matchingEngineEncoder
              .wrapAndApplyHeader(bufferClaim.buffer(), bufferClaim.offset(), messageHeaderEncoder)
              .snapshot(SnapshotType.START)
              .instrumentId(instrumentId);
          bufferClaim.commit();
          break;
        }
        checkResult(result);
        idleStrategy.idle();
      }
    }

    private void storePriceLevel(
        IdleStrategy idleStrategy, Publication publication, PriceLevel priceLevel) {
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
        idleStrategy.idle();
      }
      storeOrders(idleStrategy, publication, priceLevel);
    }

    private void storeOrders(
        IdleStrategy idleStrategy, Publication publication, PriceLevel priceLevel) {
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
          idleStrategy.idle();
        }
      }
    }

    private void markEnd(IdleStrategy idleStrategy, Publication publication) {
      int length = MessageHeaderEncoder.ENCODED_LENGTH + MatchingEngineEncoder.BLOCK_LENGTH;
      while (true) {
        final long result = publication.tryClaim(length, bufferClaim);

        if (result > 0) {
          matchingEngineEncoder
              .wrapAndApplyHeader(bufferClaim.buffer(), bufferClaim.offset(), messageHeaderEncoder)
              .snapshot(SnapshotType.END)
              .instrumentId(instrumentId);
          bufferClaim.commit();
          break;
        }
        checkResult(result);
        idleStrategy.idle();
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

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("FifoMatchingEngine{");
    sb.append("instrumentId='").append(instrumentId).append('\'');
    sb.append(", bids=[");

    bids.values()
        .forEach(
            priceLevel -> {
              sb.append("{ priceLevel=").append(priceLevel).append(", orders= [");
              priceLevel.orders.forEach(sb::append);
              sb.append("]}");
            });

    sb.append("]").append(", asks=[");

    asks.values()
        .forEach(
            priceLevel -> {
              sb.append("{ priceLevel=").append(priceLevel).append(", orders= [");
              priceLevel.orders.forEach(sb::append);
              sb.append("]}");
            });

    sb.append("]}");
    return sb.toString();
  }
}
