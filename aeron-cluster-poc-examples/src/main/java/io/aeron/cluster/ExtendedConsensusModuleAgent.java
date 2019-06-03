package io.aeron.cluster;

import io.aeron.cluster.ConsensusModule.Context;

public class ExtendedConsensusModuleAgent extends ConsensusModuleAgent {

  private ExtendedConsensusModuleAgent(Context ctx) {
    super(ctx);
  }

  public static ExtendedConsensusModuleAgent create(Context ctx) {
    ctx.conclude();
    return new ExtendedConsensusModuleAgent(ctx);
  }
}
