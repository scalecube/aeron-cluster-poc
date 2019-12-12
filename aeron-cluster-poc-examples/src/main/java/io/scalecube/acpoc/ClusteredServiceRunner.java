package io.scalecube.acpoc;

import io.aeron.agent.EventConfiguration;
import io.aeron.agent.EventLogAgent;
import io.aeron.archive.Archive;
import io.aeron.archive.client.AeronArchive;
import io.aeron.cluster.ClusteredMediaDriver;
import io.aeron.cluster.ConsensusModule;
import io.aeron.cluster.ConsensusModule.Configuration;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.cluster.service.ClusteredServiceContainer;
import io.aeron.driver.DefaultAllowTerminationValidator;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.MinMulticastFlowControlSupplier;
import io.scalecube.acpoc.configuration.ConfigRegistryConfiguration;
import io.scalecube.acpoc.coordinator.ScalecubeServiceCoordinator;
import io.scalecube.config.ConfigRegistry;
import io.scalecube.net.Address;
import io.scalecube.services.ServiceEndpoint;
import io.scalecube.services.discovery.ScalecubeServiceDiscovery;
import java.io.File;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import net.bytebuddy.agent.ByteBuddyAgent;
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
    ConfigRegistry configRegistry = ConfigRegistryConfiguration.configRegistry();
    Config config =
        configRegistry.objectProperty("io.scalecube.acpoc", Config.class).value(new Config());

    // Create self endpoint
    ServiceEndpoint serviceEndpoint =
        ServiceEndpoint.builder()
            .id(config.instanceId)
            .address(Address.create(config.host(), config.basePort()))
            .tags(Collections.singletonMap("types", "exchange-gateway"))
            .build();

    ScalecubeServiceCoordinator serviceDiscoveryBootstrap =
        new ScalecubeServiceCoordinator(endpoint -> newServiceDiscovery(config, endpoint));

    serviceDiscoveryBootstrap.start(serviceEndpoint, ).then();

    System.setProperty(EventConfiguration.ENABLED_CLUSTER_EVENT_CODES_PROP_NAME, "all");
    System.setProperty(EventConfiguration.ENABLED_ARCHIVE_EVENT_CODES_PROP_NAME, "all");
    System.setProperty(EventConfiguration.ENABLED_EVENT_CODES_PROP_NAME, "admin");
    EventLogAgent.agentmain("", ByteBuddyAgent.install());

    String clusterMemberId = Integer.toHexString(Configuration.clusterMemberId());
    String nodeId = "node-" + clusterMemberId + "-" + Runners.instanceId();
    String nodeDirName = Paths.get("target", "aeron", "cluster", nodeId).toString();

    System.out.println("Cluster node directory: " + nodeDirName);

    MediaDriver.Context mediaDriverContext =
        new MediaDriver.Context()
            .errorHandler(ex -> logger.error("Exception occurred at MediaDriver: ", ex))
            .terminationHook(() -> logger.info("TerminationHook called on MediaDriver "))
            .terminationValidator(new DefaultAllowTerminationValidator())
            .warnIfDirectoryExists(true)
            .dirDeleteOnStart(true)
            .dirDeleteOnShutdown(true)
            .multicastFlowControlSupplier(new MinMulticastFlowControlSupplier());

    AeronArchive.Context aeronArchiveContext =
        new AeronArchive.Context().aeronDirectoryName(mediaDriverContext.aeronDirectoryName());

    Archive.Context archiveContext =
        new Archive.Context()
            .errorHandler(ex -> logger.error("Exception occurred at Archive: ", ex))
            .maxCatalogEntries(Configurations.MAX_CATALOG_ENTRIES)
            .aeronDirectoryName(mediaDriverContext.aeronDirectoryName())
            .archiveDir(new File(nodeDirName, "archive"))
            .controlChannel(aeronArchiveContext.controlRequestChannel())
            .controlStreamId(aeronArchiveContext.controlRequestStreamId())
            .localControlStreamId(aeronArchiveContext.controlRequestStreamId())
            .recordingEventsChannel(aeronArchiveContext.recordingEventsChannel());

    ConsensusModule.Context consensusModuleContext =
        new ConsensusModule.Context()
            .errorHandler(ex -> logger.error("Exception occurred at ConsensusModule: ", ex))
            .terminationHook(() -> logger.info("TerminationHook called on ConsensusModule"))
            .aeronDirectoryName(mediaDriverContext.aeronDirectoryName())
            .clusterDir(new File(nodeDirName, "consensus"))
            .archiveContext(aeronArchiveContext.clone());

    ClusteredMediaDriver clusteredMediaDriver =
        ClusteredMediaDriver.launch(mediaDriverContext, archiveContext, consensusModuleContext);

    ClusteredService clusteredService =
        new ClusteredServiceImpl(clusteredMediaDriver.mediaDriver().context().countersManager());

    ClusteredServiceContainer.Context clusteredServiceCtx =
        new ClusteredServiceContainer.Context()
            .errorHandler(ex -> logger.error("Exception occurred: " + ex, ex))
            .aeronDirectoryName(clusteredMediaDriver.mediaDriver().aeronDirectoryName())
            .archiveContext(aeronArchiveContext.clone())
            .clusterDir(new File(nodeDirName, "service"))
            .clusteredService(clusteredService);

    ClusteredServiceContainer clusteredServiceContainer =
        ClusteredServiceContainer.launch(clusteredServiceCtx);

    Mono<Void> onShutdown =
        Runners.onShutdown(
            () -> {
              CloseHelper.close(clusteredServiceContainer);
              CloseHelper.close(clusteredMediaDriver);
              return null;
            });
    onShutdown.block();
  }

  public static class Config {

    private String instanceId = "clustered-service-runner-" + System.nanoTime();
    private int basePort = 9000;
    private int discoveryPort = 4800;
    private String host = Address.getLocalIpAddress().getHostAddress();
    private List<String> seedMembers = Collections.emptyList();
    private int membershipSyncInterval = 3000;
    private String memberAlias = "clustered-service-runner";

    public String instanceId() {
      return instanceId;
    }

    public int basePort() {
      return basePort;
    }

    public String host() {
      return host;
    }

    public int discoveryPort() {
      return discoveryPort;
    }

    public List<Address> seedMembers() {
      return seedMembers.stream().map(Address::from).collect(Collectors.toList());
    }

    public int membershipSyncInterval() {
      return membershipSyncInterval;
    }

    public String memberAlias() {
      return memberAlias;
    }

    @Override
    public String toString() {
      return new StringJoiner(", ", Config.class.getSimpleName() + "[", "]")
          .add("instanceId='" + instanceId + "'")
          .add("basePort=" + basePort)
          .add("discoveryPort=" + discoveryPort)
          .add("host='" + host + "'")
          .add("seedMembers=" + seedMembers)
          .add("membershipSyncInterval=" + membershipSyncInterval)
          .add("memberAlias='" + memberAlias + "'")
          .toString();
    }
  }

  private static ScalecubeServiceDiscovery newServiceDiscovery(
      Config config, ServiceEndpoint endpoint) {
    return new ScalecubeServiceDiscovery(endpoint)
        .options(
            options ->
                options
                    .memberAlias(config.memberAlias())
                    .membership(m -> m.seedMembers(config.seedMembers()))
                    .membership(m -> m.syncInterval(config.membershipSyncInterval()))
                    .transport(t -> t.port(config.discoveryPort())));
  }
}
