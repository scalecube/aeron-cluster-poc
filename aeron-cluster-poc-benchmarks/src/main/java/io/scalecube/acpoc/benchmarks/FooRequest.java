package io.scalecube.acpoc.benchmarks;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

public class FooRequest {

  public final int brokerId;
  public final long brokerOrderId;
  public final String symbol;
  public final BigDecimal price;
  public final BigDecimal quantity;
  public final int side;
  public final int type;
  public final String userId;

  FooRequest(
      int brokerId,
      long brokerOrderId,
      String symbol,
      BigDecimal price,
      BigDecimal quantity,
      int side,
      int type,
      String userId) {
    this.brokerId = brokerId;
    this.brokerOrderId = brokerOrderId;
    this.symbol = symbol;
    this.price = price;
    this.quantity = quantity;
    this.side = side;
    this.type = type;
    this.userId = userId;
  }

  /**
   * Creates foor request.
   *
   * @return foo request
   */
  public static FooRequest newFooRequest() {
    return new FooRequest(
        randomId(),
        randomId(),
        "USDXYZBTC",
        BigDecimal.valueOf(randomId()),
        BigDecimal.valueOf(randomId()),
        randomId(),
        randomId(),
        "12345678absfrtgh");
  }

  private static int randomId() {
    return ThreadLocalRandom.current().nextInt(100500);
  }

  @Override
  public String toString() {
    return "FooRequest";
  }
}
