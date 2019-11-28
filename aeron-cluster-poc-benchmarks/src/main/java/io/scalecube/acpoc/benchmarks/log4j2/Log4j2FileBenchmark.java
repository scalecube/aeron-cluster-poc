package io.scalecube.acpoc.benchmarks.log4j2;

import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.slf4j.LoggerFactory;

@Fork(value = 1)
@Threads(Threads.MAX)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 15)
public class Log4j2FileBenchmark {

  private static final int OPERATIONS_PER_INVOCATION = 1000;

  @State(Scope.Benchmark)
  public static class SharedState extends LoggerBenchmarkSharedState {

    private static final String PATTERN = "%level{length=1} %d{ISO8601} %c{1.} %m [%t]%n";
    private static final String LOG_FILENAME = Log4j2FileBenchmark.class.getSimpleName() + ".log";

    /** Setup method. */
    @Setup
    public synchronized void setUp() {
      super.setUp();

      Appender fileAppender = getAppenderByName("File");
      rootLogger.removeAppender(fileAppender);
      BenchmarkAppender benchmarkAppender = new BenchmarkAppender(fileAppender::append);
      benchmarkAppender.start();
      rootLogger.addAppender(benchmarkAppender);
      logger = LoggerFactory.getLogger(Log4j2FileBenchmark.class);
    }

    @Override
    public synchronized void tearDown() {
      super.tearDown();
    }

    @Override
    LoggerContext createLoggerContext() {
      ConfigurationBuilder<BuiltConfiguration> builder =
          ConfigurationBuilderFactory.newConfigurationBuilder();
      builder.setStatusLevel(Level.ERROR);

      AppenderComponentBuilder appenderBuilder =
          builder
              .newAppender("File", "File")
              .addAttribute("fileName", LOG_FILENAME)
              .addAttribute("immediateFlush", false)
              .add(builder.newLayout("PatternLayout").addAttribute("pattern", PATTERN));
      builder.add(appenderBuilder);
      builder.add(builder.newAsyncRootLogger(Level.DEBUG).add(builder.newAppenderRef("File")));

      return Configurator.initialize(builder.build());
    }
  }

  @Benchmark
  @OutputTimeUnit(TimeUnit.NANOSECONDS)
  @BenchmarkMode({Mode.SampleTime})
  @OperationsPerInvocation(OPERATIONS_PER_INVOCATION)
  public void testTime(SharedState sharedState) {
    test(sharedState);
  }

  @Benchmark
  @OutputTimeUnit(TimeUnit.SECONDS)
  @BenchmarkMode({Mode.Throughput})
  @OperationsPerInvocation(OPERATIONS_PER_INVOCATION)
  public void testTps(SharedState sharedState) {
    test(sharedState);
  }

  private void test(SharedState sharedState) {
    for (int i = 0; i < OPERATIONS_PER_INVOCATION; i++) {
      sharedState.logger.debug("REQ placeOrder: {}", sharedState.fooRequest);
    }
  }
}
