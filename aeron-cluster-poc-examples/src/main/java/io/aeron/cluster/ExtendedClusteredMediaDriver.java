package io.aeron.cluster;

import io.aeron.archive.Archive;
import io.aeron.driver.MediaDriver;

public class ExtendedClusteredMediaDriver extends ClusteredMediaDriver {

  public ExtendedClusteredMediaDriver(
      MediaDriver driver, Archive archive, ConsensusModule consensusModule) {
    super(driver, archive, consensusModule);
  }
}
