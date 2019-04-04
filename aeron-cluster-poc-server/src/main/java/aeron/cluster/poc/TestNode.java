package aeron.cluster.poc;

import io.aeron.cluster.ClusteredMediaDriver;
import io.aeron.cluster.service.ClusteredServiceContainer;
import org.agrona.CloseHelper;

/**
 * Simplified copy-paste from aeron-cluster
 */
public class TestNode implements AutoCloseable {

  private final ClusteredMediaDriver clusteredMediaDriver;
  private final ClusteredServiceContainer container;
  private boolean isClosed = false;


  public TestNode(ClusteredMediaDriver clusteredMediaDriver,
      ClusteredServiceContainer container) {
    this.clusteredMediaDriver = clusteredMediaDriver;
    this.container = container;
  }

  public void close() {
    if (!isClosed) {
      CloseHelper.close(clusteredMediaDriver);
      CloseHelper.close(container);

      if (null != clusteredMediaDriver) {
        clusteredMediaDriver.mediaDriver().context().deleteAeronDirectory();
      }

      if (null != clusteredMediaDriver) {
        clusteredMediaDriver.consensusModule().context().deleteDirectory();
        clusteredMediaDriver.archive().context().deleteArchiveDirectory();
      }

      if (null != container) {
        container.context().deleteDirectory();
      }

      isClosed = true;
    }
  }
}
