package io.scalecube.acpoc;

import io.aeron.driver.MediaDriver;
import io.aeron.driver.MinMulticastFlowControlSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MediaDriverRunner {

  private static final Logger logger = LoggerFactory.getLogger(MediaDriverRunner.class);

  /**
   * Main function runner.
   *
   * @param args arguments
   */
  public static void main(String[] args) throws InterruptedException {
    try (MediaDriver mediaDriver =
        MediaDriver.launch(
            new MediaDriver.Context()
                .errorHandler(ex -> logger.error("Exception occurred at MediaDriver: ", ex))
                .warnIfDirectoryExists(false)
                .dirDeleteOnStart(true)
                .dirDeleteOnShutdown(true)
                .spiesSimulateConnection(true)
                .multicastFlowControlSupplier(new MinMulticastFlowControlSupplier()))) {
      System.out.println("mediaDriver.aeronDirectoryName: " + mediaDriver.aeronDirectoryName());

      Thread.currentThread().join();
    }
  }
}
