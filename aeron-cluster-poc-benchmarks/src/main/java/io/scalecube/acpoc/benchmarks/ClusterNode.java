package io.scalecube.acpoc.benchmarks;

import io.aeron.CommonContext;
import io.aeron.archive.Archive;
import io.aeron.archive.client.AeronArchive;
import io.aeron.cluster.ClusteredMediaDriver;
import io.aeron.cluster.ConsensusModule;
import io.aeron.cluster.ConsensusModule.Configuration;
import io.aeron.cluster.service.ClusteredServiceContainer;
import io.aeron.driver.MediaDriver;
import io.scalecube.net.Address;
import io.scalecube.services.ServiceEndpoint;
import io.scalecube.services.discovery.ScalecubeServiceDiscovery;
import io.scalecube.services.discovery.api.ServiceDiscovery;
import java.io.File;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.agrona.CloseHelper;
import org.agrona.IoUtil;

public class ClusterNode implements AutoCloseable {

  private ClusteredMediaDriver nodeClusteredMediaDriver;
  private ClusteredServiceContainer nodeClusteredServiceContainer;

  /**
   * Main method to run cluster node separately.
   *
   * @param args args
   */
  public static void main(String[] args) throws InterruptedException {
    Address member = Address.create(Runners.HOST_ADDRESS, Runners.NODE_BASE_PORT);
    List<Address> clusterMembers = Collections.singletonList(member);
    if (Runners.CONNECT_VIA_SEED) {
      ServiceDiscovery serviceDiscovery =
          new ScalecubeServiceDiscovery(
                  ServiceEndpoint.builder()
                      .id(UUID.randomUUID().toString())
                      .serviceGroup("ClusterNode", Runners.CLUSTER_GROUP_SIZE)
                      .address(member)
                      .build())
              .options(
                  options -> options.membership(cfg -> cfg.seedMembers(Runners.seedMembers())));

      if (Runners.CLUSTER_GROUP_SIZE > 1) {
        clusterMembers = ClusterMember.endpoints(serviceDiscovery).block(Duration.ofMinutes(10));
      } else {
        serviceDiscovery
            .start()
            .subscribe(
                self -> System.out.println("service discovery listen on " + self.address()),
                Throwable::printStackTrace);
      }
    }
    try (ClusterNode clusterNode = new ClusterNode(member, clusterMembers)) {
      Thread.currentThread().join();
    }
  }

  /** Launches cluster node. */
  public static ClusterNode launch() {
    if (Runners.CONNECT_VIA_SEED) {
      return null;
    }
    Address member = Address.create(Runners.HOST_ADDRESS, Runners.NODE_BASE_PORT);
    List<Address> clusterMembers = Collections.singletonList(member);
    return new ClusterNode(member, clusterMembers);
  }

  private ClusterNode(Address address, List<Address> clusterMembers) {
    try {
      start(address, clusterMembers);
    } catch (Throwable th) {
      close();
      throw th;
    }
  }

  private void start(Address address, List<Address> clusterMembers) {
    String clusterMemberId = Integer.toHexString(Configuration.clusterMemberId());
    String nodeId = "node-" + clusterMemberId + "-" + UUID.randomUUID();
    String nodeDirName = Paths.get(IoUtil.tmpDirName(), "aeron", "cluster", nodeId).toString();
    String nodeAeronDirectoryName =
        Paths.get(CommonContext.getAeronDirectoryName(), "media", nodeId).toString();
    System.out.println("Cluster node directory: " + nodeDirName);

    ClusteredServiceAddressing addressing = new ClusteredServiceAddressing(address);

    AeronArchive.Context aeronArchiveContext =
        new AeronArchive.Context()
            .aeronDirectoryName(nodeAeronDirectoryName)
            .controlRequestChannel(addressing.archiveControlRequestChannel())
            .controlRequestStreamId(addressing.archiveControlRequestStreamId())
            .controlResponseChannel(addressing.archiveControlResponseChannel())
            .controlResponseStreamId(addressing.archiveControlResponseStreamId())
            .recordingEventsChannel(addressing.archiveRecordingEventsChannel())
            .errorHandler(Throwable::printStackTrace);

    nodeClusteredMediaDriver =
        ClusteredMediaDriver.launch(
            new MediaDriver.Context()
                .aeronDirectoryName(nodeAeronDirectoryName)
                .dirDeleteOnStart(true)
                .dirDeleteOnShutdown(true)
                .warnIfDirectoryExists(true)
                .printConfigurationOnStart(true)
                .errorHandler(Throwable::printStackTrace),
            new Archive.Context()
                .aeronDirectoryName(nodeAeronDirectoryName)
                .archiveDir(new File(nodeDirName, "archive"))
                .deleteArchiveOnStart(true)
                .controlChannel(aeronArchiveContext.controlRequestChannel())
                .controlStreamId(aeronArchiveContext.controlRequestStreamId())
                .localControlStreamId(aeronArchiveContext.controlRequestStreamId())
                .recordingEventsChannel(aeronArchiveContext.recordingEventsChannel())
                .recordingEventsStreamId(aeronArchiveContext.recordingEventsStreamId())
                .localControlChannel("aeron:ipc?term-length=64k")
                .errorHandler(Throwable::printStackTrace),
            new ConsensusModule.Context()
                .aeronDirectoryName(nodeAeronDirectoryName)
                .clusterDir(new File(nodeDirName, "consensus-module"))
                .deleteDirOnStart(true)
                .archiveContext(aeronArchiveContext.clone())
                .clusterMemberId(address.hashCode())
                .clusterMembers(ClusteredServiceAddressing.toClusterMembers(clusterMembers))
                .ingressChannel("aeron:udp?term-length=64k")
                .logChannel(addressing.logChannel())
                .errorHandler(Throwable::printStackTrace));

    nodeClusteredServiceContainer =
        ClusteredServiceContainer.launch(
            new ClusteredServiceContainer.Context()
                .aeronDirectoryName(nodeAeronDirectoryName)
                .archiveContext(aeronArchiveContext.clone())
                .clusterDir(new File(nodeDirName, "service"))
                .clusteredService(new ClusteredServiceImpl())
                .errorHandler(Throwable::printStackTrace));
  }

  @Override
  public void close() {
    CloseHelper.quietClose(nodeClusteredServiceContainer);
    CloseHelper.quietClose(nodeClusteredMediaDriver);
  }
}
