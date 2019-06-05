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

    String aeronDirectoryName0 = Paths.get(nodeDirName, "media-0").toString();
    String aeronDirectoryName1 = Paths.get(nodeDirName, "media-1").toString();

    ClusteredServiceAddressing addressing0 =
        new ClusteredServiceAddressing(Address.create("localhost", 8000));
    ClusteredServiceAddressing addressing1 =
        new ClusteredServiceAddressing(Address.create("localhost", 9000));

    System.out.println("addressing0: " + addressing0);
    System.out.println("addressing1: " + addressing1);

    MediaDriver.Context mediaDriverContext0 = mediaDriverContext(aeronDirectoryName0);
    MediaDriver mediaDriver0 = MediaDriver.launch(mediaDriverContext0);
    CountersManager countersManager0 = mediaDriver0.context().countersManager();

    MediaDriver.Context mediaDriverContext1 = mediaDriverContext(aeronDirectoryName1);
    MediaDriver mediaDriver1 = MediaDriver.launch(mediaDriverContext1);
    CountersManager countersManager1 = mediaDriver1.context().countersManager();

    AeronArchive.Context aeronArchiveContext0 =
        aeronArchiveContext(addressing0, aeronDirectoryName0);
    AeronArchive.Context aeronArchiveContext1 =
        aeronArchiveContext(addressing1, aeronDirectoryName1);

    ConsensusModule.Context consensusModuleContext0 =
        consensusModuleContext(
            0, addressing0, nodeDirName, aeronDirectoryName0, aeronArchiveContext0.clone());
    ConsensusModule.Context consensusModuleContext1 =
        consensusModuleContext(
            1, addressing1, nodeDirName, aeronDirectoryName1, aeronArchiveContext1.clone());

    Archive.Context archiveContext0 =
        archiveContext(0, nodeDirName, aeronDirectoryName0, aeronArchiveContext0.clone());
    Archive.Context archiveContext1 =
        archiveContext(1, nodeDirName, aeronDirectoryName1, aeronArchiveContext1.clone());

    ClusteredServiceContainer.Context clusteredServiceContext0 =
        clusteredServiceContext(
            0, nodeDirName, aeronDirectoryName0, aeronArchiveContext0.clone(), countersManager0);
    ClusteredServiceContainer.Context clusteredServiceContext1 =
        clusteredServiceContext(
            1, nodeDirName, aeronDirectoryName1, aeronArchiveContext1.clone(), countersManager1);

    AgentRunner.startOnThread(
        new AgentRunner(
            archiveContext0.idleStrategy(),
            archiveContext0.errorHandler(),
            archiveContext0.errorCounter(),
            new DynamicCompositeAgent(
                "compositeArchiveAgent",
                createArchiveAgent(archiveContext0, mediaDriverContext0),
                createArchiveAgent(archiveContext1, mediaDriverContext1))));

    AgentRunner.startOnThread(
        new AgentRunner(
            consensusModuleContext0.idleStrategy(),
            consensusModuleContext0.errorHandler(),
            consensusModuleContext0.errorCounter(),
            new DynamicCompositeAgent(
                "compositeConsensusModuleAgent",
                ExtendedConsensusModuleAgent.create(consensusModuleContext0),
                ExtendedConsensusModuleAgent.create(consensusModuleContext1))));

    AgentRunner.startOnThread(
        new AgentRunner(
            clusteredServiceContext0.idleStrategy(),
            clusteredServiceContext0.errorHandler(),
            clusteredServiceContext0.errorCounter(),
            new DynamicCompositeAgent(
                "compositeClusteredServiceAgent",
                ExtendedClusteredServiceAgent.create(clusteredServiceContext0),
                ExtendedClusteredServiceAgent.create(clusteredServiceContext1))));

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
      CountersManager countersManager) {
    return new ClusteredServiceContainer.Context()
        .errorHandler(ex -> logger.error("Exception occurred: " + ex, ex))
        .idleStrategySupplier(Archive.Configuration.idleStrategySupplier(null))
        .aeronDirectoryName(aeronDirectoryName)
        .archiveContext(aeronArchiveContext)
        .clusterDir(new File(nodeDirName, "service-" + instance))
        .serviceId(instance)
        .serviceName(Integer.toHexString(instance))
        .clusteredService(new ClusteredServiceImpl(countersManager));
  }

  private static ConsensusModule.Context consensusModuleContext(
      int instance,
      ClusteredServiceAddressing addressing,
      String nodeDirName,
      String aeronDirectoryName,
      AeronArchive.Context aeronArchiveContext) {
    return new ConsensusModule.Context()
        .errorHandler(ex -> logger.error("Exception occurred: " + ex, ex))
        .idleStrategySupplier(Archive.Configuration.idleStrategySupplier(null))
        .aeronDirectoryName(aeronDirectoryName)
        .clusterDir(new File(nodeDirName, "consensus-module-" + instance))
        .archiveContext(aeronArchiveContext)
        .ingressChannel("aeron:udp?term-length=64k")
        .logChannel(addressing.logChannel())
        .clusterMemberId(addressing.address.hashCode())
        .clusterMembers(
            ClusteredServiceAddressing.toClusterMembers(
                Collections.singletonList(addressing.address)));
  }

  private static Archive.Context archiveContext(
      int instance,
      String nodeDirName,
      String aeronDirectoryName,
      AeronArchive.Context aeronArchiveContext) {
    return new Archive.Context()
        .errorHandler(ex -> logger.error("Exception occurred: " + ex, ex))
        .idleStrategySupplier(Archive.Configuration.idleStrategySupplier(null))
        .maxCatalogEntries(Configurations.MAX_CATALOG_ENTRIES)
        .aeronDirectoryName(aeronDirectoryName)
        .archiveDir(new File(nodeDirName, "archive-" + instance))
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
