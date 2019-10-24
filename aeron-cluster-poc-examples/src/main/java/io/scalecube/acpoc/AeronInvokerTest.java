package io.scalecube.acpoc;

import static io.aeron.driver.status.SystemCounterDescriptor.SYSTEM_COUNTER_TYPE_ID;

import io.aeron.Aeron;
import io.aeron.Counter;
import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.archive.client.AeronArchive;
import io.aeron.cluster.ConsensusModule;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.MediaDriver.Context;
import io.aeron.driver.ThreadingMode;
import java.lang.reflect.Constructor;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.CompositeAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    Counter archiveErrors = aeron.addCounter(SYSTEM_COUNTER_TYPE_ID, "Archive errors");
    Counter clusterErrors = aeron.addCounter(SYSTEM_COUNTER_TYPE_ID, "Cluster errors");

    // aeronArchiveContext
    AeronArchive.Context aeronArchiveContext =
        new AeronArchive.Context().aeronDirectoryName(aeronDirectoryName);

    // Archive
    Archive archive =
        Archive.launch(
            new Archive.Context()
                .aeron(aeron)
                .errorCounter(archiveErrors)
                .errorHandler(ex -> LOGGER.error("Exception occurred on Archive: ", ex))
                .aeronDirectoryName(aeronDirectoryName)
                .controlChannel(aeronArchiveContext.controlRequestChannel())
                .controlStreamId(aeronArchiveContext.controlRequestStreamId())
                .localControlStreamId(aeronArchiveContext.controlRequestStreamId())
                .recordingEventsChannel(aeronArchiveContext.recordingEventsChannel())
                .threadingMode(ArchiveThreadingMode.INVOKER)
                .mediaDriverAgentInvoker(mediaDriver.sharedAgentInvoker())
                .deleteArchiveOnStart(true));

    // archiveInvoker

    // consensusModuleContext
    ConsensusModule.Context consensusModuleContext =
        new ConsensusModule.Context()
            .aeron(aeron)
            .errorCounter(clusterErrors)
            .errorHandler(ex -> LOGGER.error("Exception occurred on ConsensusModule: ", ex))
            .aeronDirectoryName(aeronDirectoryName)
            .archiveContext(aeronArchiveContext.clone());

    consensusModuleContext.conclude();

    Class<?> aclass = Class.forName("io.aeron.cluster.ConsensusModuleAgent");
    Constructor<?> constructor = aclass.getDeclaredConstructor(ConsensusModule.Context.class);
    constructor.setAccessible(true);
    Agent consensusModuleAgent = (Agent) constructor.newInstance(consensusModuleContext);

    AgentRunner agentRunner =
        new AgentRunner(
            new BusySpinIdleStrategy(),
            ex -> LOGGER.error("Exception occurred on AgentRunner: ", ex),
            null,
            new CompositeAgent(
                mediaDriver.sharedAgentInvoker().agent(),
                archive.invoker().agent(),
                consensusModuleAgent));
    AgentRunner.startOnThread(agentRunner);

    Thread.currentThread().join();
  }
}
