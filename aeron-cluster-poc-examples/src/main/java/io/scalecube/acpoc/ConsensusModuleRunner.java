package io.scalecube.acpoc;

import io.aeron.cluster.ConsensusModule;
import io.aeron.cluster.service.ClusteredServiceContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main class that starts single node in cluster, though expecting most of cluster configuration
 * passed via VM args.
 */
public class ConsensusModuleRunner {

  private static final Logger logger = LoggerFactory.getLogger(ConsensusModuleRunner.class);

  /**
   * Main function runner.
   *
   * @param args arguments
   */
  public static void main(String[] args) throws InterruptedException {

    try (final ConsensusModule consensusModule =
            ConsensusModule.launch(
                new ConsensusModule.Context()
                    .errorHandler(
                        th -> logger.error("Exception occurred at ConsensusModule: ", th)));
        ClusteredServiceContainer clusteredServiceContainer =
            ClusteredServiceContainer.launch(
                new ClusteredServiceContainer.Context()
                    .errorHandler(
                        ex -> logger.error("Exception occurred at ClusteredServiceContainer: ", ex))
                    .clusteredService(new ClusteredServiceImpl(null)))) {

      System.out.println(
          "consensusModule.context().aeronDirectoryName: "
              + consensusModule.context().aeronDirectoryName());
      System.out.println(
          "consensusModule.context().clusterDir: " + consensusModule.context().clusterDir());
      System.out.println(
          "consensusModule.context().clusterDirectoryName: "
              + consensusModule.context().clusterDirectoryName());

      System.out.println(
          "clusteredServiceContainer.context().aeronDirectoryName: "
              + clusteredServiceContainer.context().aeronDirectoryName());
      System.out.println(
          "clusteredServiceContainer.context().clusterDir: "
              + clusteredServiceContainer.context().clusterDir());
      System.out.println(
          "clusteredServiceContainer.context().clusterDirectoryName: "
              + clusteredServiceContainer.context().clusterDirectoryName());

      Thread.currentThread().join();
    }
  }
}
