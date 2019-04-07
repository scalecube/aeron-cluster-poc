package io.scalecube.acpoc;

import io.aeron.CommonContext;
import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.cluster.ClusteredMediaDriver;
import io.aeron.cluster.ConsensusModule;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.cluster.service.ClusteredServiceContainer;
import io.aeron.driver.MediaDriver.Context;
import io.aeron.driver.ThreadingMode;
import org.agrona.CloseHelper;
import org.agrona.ErrorHandler;

public class SingleNode implements AutoCloseable {

  private String mediaDir;

  private final ClusteredMediaDriver clusteredMediaDriver;

  private static final long MAX_CATALOG_ENTRIES = 1024;
  private final ClusteredServiceContainer container;

  public SingleNode(ClusteredService service) {
    String aeronHome =
        CommonContext.getAeronDirectoryName() + "-" + System.nanoTime();
//    this.mediaDir = aeronHome + "/media";
//    System.out.println("Using aeron media dir:" + mediaDir);

    // could be specified also
//    String archiveDir = aeronHome + "/archive";
//    String clusterDir = aeronHome + "/cluster";
//    String clusterServiceDir = aeronHome + "/service";

    this.clusteredMediaDriver = ClusteredMediaDriver.launch(
        new Context()
//            .aeronDirectoryName(mediaDir)
            .aeronDirectoryName("aeron-custom")
            .threadingMode(ThreadingMode.SHARED)
            .termBufferSparseFile(true)
            .errorHandler(errorHandler(0))
            .dirDeleteOnStart(true),
        new Archive.Context()
//            .aeronDirectoryName(mediaDir)
            .aeronDirectoryName("aeron-custom")
            .maxCatalogEntries(MAX_CATALOG_ENTRIES)
            .threadingMode(ArchiveThreadingMode.SHARED)
            .deleteArchiveOnStart(true),
        new ConsensusModule.Context()
//            .aeronDirectoryName(mediaDir)
            .aeronDirectoryName("aeron-custom")
            .errorHandler(errorHandler(0))
            .deleteDirOnStart(true));

//    this.container = wrapIntoContainer(service, mediaDir);
    this.container = wrapIntoContainer(service, "");
  }

  public String getMediaDir() {
    return mediaDir;
  }

  public ClusteredMediaDriver getClusteredMediaDriver() {
    return clusteredMediaDriver;
  }


  private static ErrorHandler errorHandler(int i) {
    return ex -> {
      System.err.println("Error in node " + i);
      ex.printStackTrace();
    };
  }

  private static ClusteredServiceContainer wrapIntoContainer(ClusteredService service,
      String mediaDir) {
    return ClusteredServiceContainer
        .launch(new ClusteredServiceContainer
            .Context()
            .clusteredService(service)
//            .aeronDirectoryName(mediaDir)
            .errorHandler(
                ex -> System.err.println("Error in service:" + ex.getLocalizedMessage())));
  }

  @Override
  public void close() throws Exception {
    CloseHelper.close(container);
    CloseHelper.close(clusteredMediaDriver);

    if (null != clusteredMediaDriver) {
      clusteredMediaDriver.consensusModule().context().deleteDirectory();
      clusteredMediaDriver.archive().context().deleteArchiveDirectory();
      clusteredMediaDriver.mediaDriver().context().deleteAeronDirectory();
    }
  }
}
