package io.scalecube.acpoc;

import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.archive.client.AeronArchive;
import io.aeron.cluster.ConsensusModule;
import io.aeron.cluster.ConsensusModule.Configuration;
import io.aeron.cluster.ExtendedConsensusModuleAgent;
import io.aeron.cluster.service.ClusteredServiceContainer;
import io.aeron.cluster.service.ExtendedClusteredServiceAgent;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.MediaDriver.Context;
import io.aeron.driver.MinMulticastFlowControlSupplier;
import io.aeron.driver.ThreadingMode;
import java.io.File;
import java.nio.file.Paths;
import java.util.Collections;
import org.agrona.IoUtil;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.DynamicCompositeAgent;
import org.agrona.concurrent.status.CountersManager;
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
    String nodeDirName = Paths.get(IoUtil.tmpDirName(), "aeron", "cluster", nodeId).toString();

    if (Configurations.CLEAN_START) {
      IoUtil.delete(new File(nodeDirName), true);
    }

    System.out.println("Cluster node directory: " + nodeDirName);

    String aeronDirectoryName = Paths.get(nodeDirName, "media").toString();

    ClusteredServiceAddressing addressing =
        new ClusteredServiceAddressing(Address.create("localhost", 8000));

    System.out.println("addressing: " + addressing);

    MediaDriver.Context mediaDriverContext = mediaDriverContext(aeronDirectoryName);
    MediaDriver mediaDriver = MediaDriver.launch(mediaDriverContext);
    CountersManager countersManager = mediaDriver.context().countersManager();
    ClusteredServiceImpl clusteredService = new ClusteredServiceImpl(countersManager);

    AeronArchive.Context aeronArchiveContext = aeronArchiveContext(addressing, aeronDirectoryName);

    ConsensusModule.Context consensusModuleContext =
        consensusModuleContext(
            addressing, nodeDirName, aeronDirectoryName, aeronArchiveContext.clone());

    Archive.Context archiveContext =
        archiveContext(nodeDirName, aeronDirectoryName, aeronArchiveContext.clone());

    ClusteredServiceContainer.Context clusteredServiceContext0 =
        clusteredServiceContext(
            0, nodeDirName, aeronDirectoryName, aeronArchiveContext.clone(), clusteredService);
    ClusteredServiceContainer.Context clusteredServiceContext1 =
        clusteredServiceContext(
            1, nodeDirName, aeronDirectoryName, aeronArchiveContext.clone(), clusteredService);
    ClusteredServiceContainer.Context clusteredServiceContext2 =
        clusteredServiceContext(
            2, nodeDirName, aeronDirectoryName, aeronArchiveContext.clone(), clusteredService);
    ClusteredServiceContainer.Context clusteredServiceContext3 =
        clusteredServiceContext(
            3, nodeDirName, aeronDirectoryName, aeronArchiveContext.clone(), clusteredService);
    ClusteredServiceContainer.Context clusteredServiceContext4 =
        clusteredServiceContext(
            4, nodeDirName, aeronDirectoryName, aeronArchiveContext.clone(), clusteredService);

    AgentRunner.startOnThread(
        new AgentRunner(
            archiveContext.idleStrategy(),
            archiveContext.errorHandler(),
            archiveContext.errorCounter(),
            new DynamicCompositeAgent(
                "archiveAgent", createArchiveAgent(archiveContext, mediaDriverContext))));

    AgentRunner.startOnThread(
        new AgentRunner(
            consensusModuleContext.idleStrategy(),
            consensusModuleContext.errorHandler(),
            consensusModuleContext.errorCounter(),
            new DynamicCompositeAgent(
                "consensusModuleAgent",
                ExtendedConsensusModuleAgent.create(consensusModuleContext))));

    AgentRunner.startOnThread(
        new AgentRunner(
            clusteredServiceContext0.idleStrategy(),
            clusteredServiceContext0.errorHandler(),
            clusteredServiceContext0.errorCounter(),
            new DynamicCompositeAgent(
                "compositeClusteredServiceAgent",
                ExtendedClusteredServiceAgent.create(clusteredServiceContext0),
                ExtendedClusteredServiceAgent.create(clusteredServiceContext1),
                ExtendedClusteredServiceAgent.create(clusteredServiceContext2),
                ExtendedClusteredServiceAgent.create(clusteredServiceContext3),
                ExtendedClusteredServiceAgent.create(clusteredServiceContext4))));

    Mono<Void> onShutdown =
        Utils.onShutdown(
            () -> {
              if (Configurations.CLEAN_SHUTDOWN) {
                IoUtil.delete(new File(nodeDirName), true);
              }
              return null;
            });
    onShutdown.block();
  }

  private static AeronArchive.Context aeronArchiveContext(
      ClusteredServiceAddressing addressing, String aeronDirectoryName) {
    return new AeronArchive.Context()
        .aeronDirectoryName(aeronDirectoryName)
        .errorHandler(ex -> logger.error("Exception occurred at AeronArchive: " + ex, ex))
        .controlRequestChannel(addressing.archiveControlRequestChannel())
        .controlRequestStreamId(addressing.archiveControlRequestStreamId())
        .controlResponseChannel(addressing.archiveControlResponseChannel())
        .controlResponseStreamId(addressing.archiveControlResponseStreamId())
        .recordingEventsChannel(addressing.archiveRecordingEventsChannel());
  }

  private static Agent createArchiveAgent(
      Archive.Context archiveContext, Context mediaDriverContext) {
    return Archive.launch(
            archiveContext
                .threadingMode(ArchiveThreadingMode.INVOKER)
                .errorHandler(mediaDriverContext.errorHandler()))
        .invoker()
        .agent();
  }

  private static ClusteredServiceContainer.Context clusteredServiceContext(
      int instance,
      String nodeDirName,
      String aeronDirectoryName,
      AeronArchive.Context aeronArchiveContext,
      ClusteredServiceImpl clusteredService) {
    return new ClusteredServiceContainer.Context()
        .errorHandler(ex -> logger.error("Exception occurred: " + ex, ex))
        .idleStrategySupplier(Archive.Configuration.idleStrategySupplier(null))
        .aeronDirectoryName(aeronDirectoryName)
        .archiveContext(aeronArchiveContext)
        .clusterDir(new File(nodeDirName, "service-" + instance))
        .serviceId(instance)
        .serviceName(Integer.toHexString(instance))
        .clusteredService(clusteredService);
  }

  private static ConsensusModule.Context consensusModuleContext(
      ClusteredServiceAddressing addressing,
      String nodeDirName,
      String aeronDirectoryName,
      AeronArchive.Context aeronArchiveContext) {
    return new ConsensusModule.Context()
        .errorHandler(ex -> logger.error("Exception occurred: " + ex, ex))
        .idleStrategySupplier(Archive.Configuration.idleStrategySupplier(null))
        .aeronDirectoryName(aeronDirectoryName)
        .clusterDir(new File(nodeDirName, "consensus-module"))
        .archiveContext(aeronArchiveContext)
        .ingressChannel("aeron:udp?term-length=64k")
        .logChannel(addressing.logChannel())
        .serviceCount(5)
        .clusterMemberId(addressing.address.hashCode())
        .clusterMembers(
            ClusteredServiceAddressing.toClusterMembers(
                Collections.singletonList(addressing.address)));
  }

  private static Archive.Context archiveContext(
      String nodeDirName, String aeronDirectoryName, AeronArchive.Context aeronArchiveContext) {
    return new Archive.Context()
        .errorHandler(ex -> logger.error("Exception occurred: " + ex, ex))
        .idleStrategySupplier(Archive.Configuration.idleStrategySupplier(null))
        .maxCatalogEntries(Configurations.MAX_CATALOG_ENTRIES)
        .aeronDirectoryName(aeronDirectoryName)
        .archiveDir(new File(nodeDirName, "archive"))
        .controlChannel(aeronArchiveContext.controlRequestChannel())
        .controlStreamId(aeronArchiveContext.controlRequestStreamId())
        .localControlStreamId(aeronArchiveContext.controlRequestStreamId())
        .recordingEventsChannel(aeronArchiveContext.recordingEventsChannel())
        .threadingMode(ArchiveThreadingMode.SHARED);
  }

  private static Context mediaDriverContext(String aeronDirectoryName) {
    return new Context()
        .errorHandler(ex -> logger.error("Exception occurred: " + ex, ex))
        .aeronDirectoryName(aeronDirectoryName)
        .threadingMode(ThreadingMode.SHARED)
        .multicastFlowControlSupplier(new MinMulticastFlowControlSupplier())
        .spiesSimulateConnection(true);
  }
}
