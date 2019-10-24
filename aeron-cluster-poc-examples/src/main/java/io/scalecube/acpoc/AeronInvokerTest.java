package io.scalecube.acpoc;

import io.aeron.Aeron;
import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.archive.client.AeronArchive;
import io.aeron.cluster.ConsensusModule;
import io.aeron.cluster.service.ClusteredServiceContainer;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.MediaDriver.Context;
import io.aeron.driver.ThreadingMode;
import org.agrona.CloseHelper;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.CompositeAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class AeronInvokerTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(AeronInvokerTest.class);

  public static void main(String[] args) throws Exception {
    // Media
    MediaDriver mediaDriver =
        MediaDriver.launch(
            new Context()
                .threadingMode(ThreadingMode.INVOKER)
                .spiesSimulateConnection(true)
                .dirDeleteOnStart(true));

    String aeronDirectoryName = mediaDriver.context().aeronDirectoryName();

    // Aeron
    Aeron aeron =
        Aeron.connect(
            new Aeron.Context()
                .driverAgentInvoker(mediaDriver.sharedAgentInvoker())
                .useConductorAgentInvoker(true)
                .idleStrategy(new BusySpinIdleStrategy()));

    //    Counter archiveErrors = aeron.addCounter(SYSTEM_COUNTER_TYPE_ID, "Archive errors");
    //    Counter clusterErrors = aeron.addCounter(SYSTEM_COUNTER_TYPE_ID, "Cluster errors");

    // aeronArchiveContext
    AeronArchive.Context aeronArchiveContext =
        new AeronArchive.Context().aeronDirectoryName(aeronDirectoryName);

    // Archive
    Archive archive =
        Archive.launch(
            new Archive.Context()
                //                .aeron(aeron)
                //                .errorCounter(archiveErrors)
                .errorHandler(ex -> LOGGER.error("Exception occurred on Archive: ", ex))
                .aeronDirectoryName(aeronDirectoryName)
                .controlChannel(aeronArchiveContext.controlRequestChannel())
                .controlStreamId(aeronArchiveContext.controlRequestStreamId())
                .localControlStreamId(aeronArchiveContext.controlRequestStreamId())
                .recordingEventsChannel(aeronArchiveContext.recordingEventsChannel())
                .threadingMode(ArchiveThreadingMode.INVOKER)
                .mediaDriverAgentInvoker(mediaDriver.sharedAgentInvoker())
                .deleteArchiveOnStart(true));

    AgentRunner.startOnThread(
        new AgentRunner(
            new BusySpinIdleStrategy(),
            ex -> LOGGER.error("Exception occurred on AgentRunner: ", ex),
            null,
            new CompositeAgent(
                mediaDriver.sharedAgentInvoker().agent(), archive.invoker().agent())));

    // consensusModuleContext
    ConsensusModule.Context consensusModuleContext =
        new ConsensusModule.Context()
            //            .aeron(aeron)
            //            .errorCounter(clusterErrors)
            .errorHandler(ex -> LOGGER.error("Exception occurred on ConsensusModule: ", ex))
            .aeronDirectoryName(aeronDirectoryName)
            .archiveContext(aeronArchiveContext.clone());

    // consensusModule
    ConsensusModule consensusModule = ConsensusModule.launch(consensusModuleContext);

    ClusteredServiceContainer.Context clusteredServiceContext =
        new ClusteredServiceContainer.Context()
            .errorHandler(
                ex -> LOGGER.error("Exception occurred on ClusteredServiceContainer: ", ex))
            .aeronDirectoryName(aeronDirectoryName)
            .archiveContext(aeronArchiveContext.clone())
            .clusteredService(new ClusteredServiceImpl(mediaDriver.context().countersManager()));

    ClusteredServiceContainer clusteredServiceContainer =
        ClusteredServiceContainer.launch(clusteredServiceContext);

    Mono<Void> onShutdown =
        Utils.onShutdown(
            () -> {
              CloseHelper.close(consensusModule);
              CloseHelper.close(clusteredServiceContainer);
              return null;
            });
    onShutdown.block();
  }
}
