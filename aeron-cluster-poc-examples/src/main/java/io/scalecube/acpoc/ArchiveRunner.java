package io.scalecube.acpoc;

import io.aeron.archive.Archive;
import io.aeron.archive.Archive.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArchiveRunner {

  private static final Logger logger = LoggerFactory.getLogger(ArchiveRunner.class);

  /**
   * Main function runner.
   *
   * @param args arguments
   */
  public static void main(String[] args) throws InterruptedException {
    try (Archive archive =
        Archive.launch(
            new Context()
                .errorHandler(ex -> logger.error("Exception occurred at Archive: ", ex)))) {

      System.out.println(
          "archive.context().aeronDirectoryName: " + archive.context().aeronDirectoryName());
      System.out.println(
          "archive.context().archiveDirectoryName: " + archive.context().archiveDirectoryName());
      System.out.println("archive.context().archiveDir(): " + archive.context().archiveDir());

      Thread.currentThread().join();
    }
  }
}
