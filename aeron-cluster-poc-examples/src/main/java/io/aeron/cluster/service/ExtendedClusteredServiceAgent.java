package io.aeron.cluster.service;

import io.aeron.cluster.service.ClusteredServiceContainer.Context;

public class ExtendedClusteredServiceAgent extends ClusteredServiceAgent {

  private ExtendedClusteredServiceAgent(Context ctx) {
    super(ctx);
  }

  public static ExtendedClusteredServiceAgent create(Context ctx) {
    ctx.conclude();
    return new ExtendedClusteredServiceAgent(ctx);
  }
}
