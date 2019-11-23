package io.scalecube.acpoc;

import java.util.Optional;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;
import sun.misc.Signal;
import sun.misc.SignalHandler;

public class Utils {

  public static final Logger logger = LoggerFactory.getLogger(Utils.class);
  private static final long INSTANCE_ID = System.currentTimeMillis();

  private Utils() {
    // no-op
  }

  /** In order to let interrupt the process, thi method is regularly called in 'waiting' loops. */
  public static void checkInterruptedStatus() {
    if (Thread.currentThread().isInterrupted()) {
      fail("unexpected interrupt - test likely to have timed out");
    }
  }

  /**
   * Fail for a reason.
   *
   * @param reason to fail
   */
  public static void fail(String reason) {
    throw new IllegalStateException(reason);
  }

  /**
   * Listens to jvm signas SIGTERM and SIGINT and applies shutdown lambda function.
   *
   * @param callable shutdown lambda
   * @return mono result
   */
  public static Mono<Void> onShutdown(Callable callable) {
    MonoProcessor<Void> onShutdown = MonoProcessor.create();

    SignalHandler handler =
        signal -> {
          try {
            callable.call();
          } catch (Exception e) {
            logger.warn("Exception occurred at onShutdown callback: " + e, e);
          } finally {
            onShutdown.onComplete();
          }
        };
    Signal.handle(new Signal("INT"), handler);
    Signal.handle(new Signal("TERM"), handler);

    return onShutdown;
  }

  /**
   * Returns instance id.
   *
   * @return instance id
   */
  public static String instanceId() {
    return Optional.ofNullable(Configurations.INSTANCE_ID).orElseGet(() -> "" + INSTANCE_ID);
  }
}
