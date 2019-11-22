package io.scalecube.acpoc.benchmarks;

import static java.lang.System.getProperty;

import io.aeron.driver.Configuration;
import org.agrona.CloseHelper;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.CompositeAgent;
import org.agrona.concurrent.IdleStrategy;

public class SenderReceiverAgentRunner implements AutoCloseable {

  enum ThreadingMode {
    SHARED,
    DEDICATED
  }

  public static final String BENCHMARK_THREADING_MODE_PROPERTY = "benchmark.threading.mode";
  public static final String BENCHMARK_THREADING_MODE_DEFAULT = "SHARED";
  public static final String BENCHMARK_SHARED_IDLE_STRATEGY_PROPERTY =
      "benchmark.shared.idle.strategy";
  public static final String BENCHMARK_RECEIVER_IDLE_STRATEGY_PROPERTY =
      "benchmark.receiver.idle.strategy";
  public static final String BENCHMARK_SENDER_IDLE_STRATEGY_PROPERTY =
      "benchmark.sender.idle.strategy";

  public static final String DEFAULT_IDLE_STRATEGY = "org.agrona.concurrent.BackoffIdleStrategy";

  private final AgentRunner sharedAgentRunner;
  private final AgentRunner senderAgentRunner;
  private final AgentRunner receiverAgentRunner;

  public static SenderReceiverAgentRunner launch(Agent senderAgent, Agent receiverAgent) {
    return new SenderReceiverAgentRunner(senderAgent, receiverAgent);
  }

  private SenderReceiverAgentRunner(Agent senderAgent, Agent receiverAgent) {
    ThreadingMode threadingMode =
        ThreadingMode.valueOf(
            getProperty(BENCHMARK_THREADING_MODE_PROPERTY, BENCHMARK_THREADING_MODE_DEFAULT));
    switch (threadingMode) {
      case SHARED:
        IdleStrategy sharedIdleStrategy =
            Configuration.agentIdleStrategy(
                getProperty(BENCHMARK_SHARED_IDLE_STRATEGY_PROPERTY, DEFAULT_IDLE_STRATEGY), null);
        sharedAgentRunner =
            new AgentRunner(
                sharedIdleStrategy,
                Throwable::printStackTrace,
                null,
                new CompositeAgent(senderAgent, receiverAgent));
        senderAgentRunner = null;
        receiverAgentRunner = null;
        AgentRunner.startOnThread(sharedAgentRunner);
        break;
      case DEDICATED:
        sharedAgentRunner = null;
        IdleStrategy receiverIdleStrategy =
            Configuration.agentIdleStrategy(
                getProperty(BENCHMARK_RECEIVER_IDLE_STRATEGY_PROPERTY, DEFAULT_IDLE_STRATEGY),
                null);
        receiverAgentRunner =
            new AgentRunner(receiverIdleStrategy, Throwable::printStackTrace, null, receiverAgent);
        IdleStrategy senderIdleStrategy =
            Configuration.agentIdleStrategy(
                getProperty(BENCHMARK_SENDER_IDLE_STRATEGY_PROPERTY, DEFAULT_IDLE_STRATEGY), null);
        senderAgentRunner =
            new AgentRunner(senderIdleStrategy, Throwable::printStackTrace, null, senderAgent);
        AgentRunner.startOnThread(receiverAgentRunner);
        AgentRunner.startOnThread(senderAgentRunner);
        break;
      default:
        throw new IllegalArgumentException("ThreadingMode: " + threadingMode);
    }
  }

  @Override
  public void close() {
    CloseHelper.quietCloseAll(sharedAgentRunner, senderAgentRunner, receiverAgentRunner);
  }
}
