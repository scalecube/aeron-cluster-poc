package io.scalecube.acpoc.snapshotting;

import java.util.ArrayDeque;
import java.util.Queue;
import om2.exchange.marketdata.match.fifo.snapshotting.OrderSide;

public class PriceLevel {

  public final OrderSide side;
  public final long price;
  public final Queue<Order> orders = new ArrayDeque<>();

  public PriceLevel(OrderSide side, long price) {
    this.side = side;
    this.price = price;
  }

  public void add(Order order) {
    orders.add(order);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("{");
    sb.append("side=").append(side);
    sb.append(", price=").append(price);
    sb.append('}');
    return sb.toString();
  }
}
