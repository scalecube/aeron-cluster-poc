package io.scalecube.acpoc.snapshotting;

import om2.exchange.marketdata.match.fifo.snapshotting.OrderType;

public class Order {

  public final String externalOrderId;
  public final long originalQuantity;
  public final long remainingQuantity;
  public final OrderType orderType;
  public final PriceLevel level;
  public final boolean isMarketMaker;

  public Order(
      PriceLevel level,
      String externalOrderId,
      long quantity,
      long remainingQuantity,
      OrderType orderType,
      boolean isMarketMaker) {
    this.level = level;
    this.externalOrderId = externalOrderId;
    this.originalQuantity = quantity;
    this.remainingQuantity = remainingQuantity;
    this.orderType = orderType;
    this.isMarketMaker = isMarketMaker;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("Order{");
    sb.append("externalOrderId='").append(externalOrderId).append('\'');
    sb.append(", originalQuantity=").append(originalQuantity);
    sb.append(", remainingQuantity=").append(remainingQuantity);
    sb.append(", orderType=").append(orderType);
    sb.append(", level=").append(level);
    sb.append(", isMarketMaker=").append(isMarketMaker);
    sb.append('}');
    return sb.toString();
  }
}
