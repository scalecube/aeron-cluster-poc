package io.scalecube.acpoc;

import io.aeron.CommonContext;
import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.archive.client.AeronArchive;
import io.aeron.cluster.ClusterTool;
import io.aeron.cluster.ConsensusModule;
import io.aeron.cluster.ConsensusModule.Configuration;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.cluster.service.ClusteredServiceContainer;
import io.aeron.driver.DefaultAllowTerminationValidator;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.MinMulticastFlowControlSupplier;
import io.aeron.driver.ThreadingMode;
import io.aeron.driver.status.SystemCounterDescriptor;
import java.io.File;
import java.nio.file.Paths;
import org.agrona.CloseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Main class that starts single node in cluster, though expecting most of cluster configuration
 * passed via VM args.
 */
public class ClusteredServiceRunner {

  private static final Logger logger = LoggerFactory.getLogger(ClusteredServiceRunner.class);

  /**
   * Main function runner.
   *
   * @param args arguments
   */
  public static void main(String[] args) {
    String clusterMemberId = Integer.toHexString(Configuration.clusterMemberId());
    String nodeId = "node-" + clusterMemberId + "-" + Utils.instanceId();
    String nodeDirName = Paths.get("target", "aeron", "cluster", nodeId).toString();

    final ClusteredServiceContainer clusteredServiceContainer;
    final MediaDriver mediaDriver;
    final Archive archive;
    final ConsensusModule consensusModule;

    System.out.println("Cluster node directory: " + nodeDirName);

    String aeronDirectoryName = Paths.get(nodeDirName, "media").toString();

    AeronArchive.Context aeronArchiveContext =
        new AeronArchive.Context().aeronDirectoryName(aeronDirectoryName);

    MediaDriver.Context mediaDriverContext =
        new MediaDriver.Context()
            .spiesSimulateConnection(true)
            .errorHandler(ex -> logger.error("Exception occurred at MediaDriver: ", ex))
            .terminationHook(() -> logger.info("TerminationHook called on MediaDriver "))
            .terminationValidator(new DefaultAllowTerminationValidator())
            .threadingMode(ThreadingMode.SHARED)
            .warnIfDirectoryExists(true)
            .dirDeleteOnStart(true)
            .aeronDirectoryName(aeronDirectoryName)
            .multicastFlowControlSupplier(new MinMulticastFlowControlSupplier());

    Archive.Context archiveContext =
        new Archive.Context()
            .errorHandler(ex -> logger.error("Exception occurred at Archive: ", ex))
            .maxCatalogEntries(Configurations.MAX_CATALOG_ENTRIES)
            .aeronDirectoryName(aeronDirectoryName)
            .archiveDir(new File(nodeDirName, "archive"))
            .controlChannel(aeronArchiveContext.controlRequestChannel())
            .controlStreamId(aeronArchiveContext.controlRequestStreamId())
            .localControlStreamId(aeronArchiveContext.controlRequestStreamId())
            .recordingEventsChannel(aeronArchiveContext.recordingEventsChannel())
            .threadingMode(ArchiveThreadingMode.SHARED);

    ConsensusModule.Context consensusModuleContext =
        new ConsensusModule.Context()
            .errorHandler(ex -> logger.error("Exception occurred at ConsensusModule: ", ex))
            .terminationHook(() -> logger.info("TerminationHook called on ConsensusModule"))
            .aeronDirectoryName(aeronDirectoryName)
            .clusterDir(new File(nodeDirName, "consensus"))
            .archiveContext(aeronArchiveContext.clone());

    mediaDriver = MediaDriver.launch(mediaDriverContext);

    archive =
        Archive.launch(
            archiveContext
                .mediaDriverAgentInvoker(mediaDriver.sharedAgentInvoker())
                .errorHandler(mediaDriverContext.errorHandler())
                .errorCounter(
                    mediaDriverContext.systemCounters().get(SystemCounterDescriptor.ERRORS)));

    consensusModule = ConsensusModule.launch(consensusModuleContext);

    ClusteredService clusteredService =
        new ClusteredServiceImpl(mediaDriverContext.countersManager());

    ClusteredServiceContainer.Context clusteredServiceContext =
        new ClusteredServiceContainer.Context()
            .errorHandler(
                ex -> logger.error("Exception occurred at ClusteredServiceContainer: ", ex))
            .terminationHook(
                () -> logger.info("TerminationHook called on ClusteredServiceContainer"))
            .aeronDirectoryName(aeronDirectoryName)
            .archiveContext(aeronArchiveContext.clone())
            .clusterDir(new File(nodeDirName, "service"))
            .clusteredService(clusteredService);

    clusteredServiceContainer = ClusteredServiceContainer.launch(clusteredServiceContext);

    Mono<Void> onShutdown =
        Utils.onShutdown(
            () -> {
              int memberId = Configuration.clusterMemberId();
              logger.info("removeMember: {}", memberId);
              File clusterDir = consensusModuleContext.clusterDir();
              boolean removeMember = ClusterTool.removeMember(clusterDir, memberId, false);
              logger.info("*** removeMember: {}, result: {}", memberId, removeMember);

              CloseHelper.quietClose(clusteredServiceContainer);
              CloseHelper.quietClose(consensusModule);
              CloseHelper.quietClose(archive);

              logger.info("requestDriverTermination: {}", mediaDriverContext.aeronDirectoryName());
              boolean driverTermination =
                  CommonContext.requestDriverTermination(
                      mediaDriverContext.aeronDirectory(), null, 0, 0);
              logger.info(
                  "*** requestDriverTermination: {}, result: {}",
                  mediaDriverContext.aeronDirectoryName(),
                  driverTermination);

              CloseHelper.quietClose(mediaDriver);
              return null;
            });
    onShutdown.block();
  }
}
