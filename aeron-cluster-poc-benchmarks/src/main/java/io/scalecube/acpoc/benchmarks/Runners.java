package io.scalecube.acpoc.benchmarks;

import io.scalecube.net.Address;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Runners {

  public static final String RESULTS_DIR_PROP_NAME = "resultsDir";
  public static final String INCLUDE_BENCHMARKS_PROP_NAME = "includeBenchmarks";
  public static final String FORKS_PROP_NAME = "forks";
  public static final String WARMUP_ITERATIONS_PROP_NAME = "warmup.iterations";
  public static final String WARMUP_TIME_SEC_PROP_NAME = "warmup.timeSec";
  public static final String MEASUREMENT_ITERATIONS_PROP_NAME = "measurement.iterations";
  public static final String MEASUREMENT_TIME_SEC_PROP_NAME = "measurement.timeSec";
  public static final String THREADS_PROP_NAME = "threads";
  public static final String JVM_ARGS_PROP_NAME = "jvmArgs";

  public static final String DEFAULT_RESULTS_DIR = "target/results";
  public static final int DEFAULT_FORKS = 1;
  public static final int DEFAULT_WARMUP_ITERATIONS = 3;
  public static final long DEFAULT_WARMUP_TIME_SEC = 5;
  public static final int DEFAULT_MEASUREMENT_ITERATIONS = 5;
  public static final long DEFAULT_MEASUREMENT_TIME = 15;
  public static final int DEFAULT_THREADS = -1; // Threads.MAX
  public static final String DEFAULT_ASYNC_PROFILER_EVENT = "cpu"; // cache-misses, alloc, lock

  public static final String ASYNC_PROFILER_ENABLED_PROP_NAME = "asyncProfiler.enabled";
  public static final String ASYNC_PROFILER_EVENT_PROP_NAME = "asyncProfiler.event";
  public static final String ASYNC_PROFILER_AGENT_FORMAT =
      "-agentpath:profiler/libasyncProfiler.so=start,threads,svg=total,event=%s,file=%s";

  public static final int MESSAGE_LENGTH = Integer.getInteger("benchmark.message.length", 256);

  public static final String SEEDS_PROPERTY = "benchmark.seeds";
  public static final String DEFAULT_SEEDS = "localhost:4801";
  public static final boolean CONNECT_VIA_SEED = Boolean.getBoolean("benchmark.connect.via.seed");
  public static final int CLUSTER_GROUP_SIZE =
      Integer.getInteger("benchmark.cluster.group.size", 1);

  public static final String BENCHMARK_CLIENT_BASE_PORT_PROPERTY = "benchmark.client.base.port";
  public static final int CLIENT_BASE_PORT =
      Integer.getInteger(BENCHMARK_CLIENT_BASE_PORT_PROPERTY, 9000);
  public static final String BENCHMARK_NODE_BASE_PORT_PROPERTY = "benchmark.node.base.port";
  public static final int NODE_BASE_PORT =
      Integer.getInteger(BENCHMARK_NODE_BASE_PORT_PROPERTY, 10000);
  public static final String HOST_ADDRESS = Address.getLocalIpAddress().getHostAddress();

  private Runners() {
    // Do not instantiate
  }

  /**
   * Retrurns boolean indicating whether async profiler is enabled.
   *
   * @return true or false; by default false.
   */
  public static boolean asyncProfilerEnabled() {
    return Boolean.getBoolean(ASYNC_PROFILER_ENABLED_PROP_NAME);
  }

  /**
   * Returns profiler string for agentpath to set {@code ChainedOptionsBuilder#jvmArgs(String...)}.
   *
   * @param clazz clazz
   * @return agent path string
   */
  public static String asyncProfilerAgentString(Class<?> clazz) {
    return String.format(
        ASYNC_PROFILER_AGENT_FORMAT,
        asyncProfilerEvent(),
        asyncProfilerResultFilename(clazz.getSimpleName()));
  }

  /**
   * Returns result filename to set {@code ChainedOptionsBuilder#result(java.lang.String)}.
   *
   * @param clazz clazz
   * @return result filename
   */
  public static String resultFilename(Class<?> clazz) {
    return Paths.get(getResultsDirName(), clazz.getSimpleName() + ".csv").toString();
  }

  /**
   * Returns include regexp string to set {@code ChainedOptionsBuilder#include(String)}.
   *
   * @param defaultValue default value
   * @return include regexp string
   */
  public static String includeBenchmarks(String defaultValue) {
    return System.getProperty(INCLUDE_BENCHMARKS_PROP_NAME, defaultValue);
  }

  public static int forks() {
    return Integer.getInteger(FORKS_PROP_NAME, DEFAULT_FORKS);
  }

  public static int warmupIterations() {
    return Integer.getInteger(WARMUP_ITERATIONS_PROP_NAME, DEFAULT_WARMUP_ITERATIONS);
  }

  public static Duration warmupTime() {
    return Duration.ofSeconds(Long.getLong(WARMUP_TIME_SEC_PROP_NAME, DEFAULT_WARMUP_TIME_SEC));
  }

  public static int measurementIterations() {
    return Integer.getInteger(MEASUREMENT_ITERATIONS_PROP_NAME, DEFAULT_MEASUREMENT_ITERATIONS);
  }

  public static Duration measurementTime() {
    return Duration.ofSeconds(
        Long.getLong(MEASUREMENT_TIME_SEC_PROP_NAME, DEFAULT_MEASUREMENT_TIME));
  }

  public static int threads() {
    return Integer.getInteger(THREADS_PROP_NAME, DEFAULT_THREADS);
  }

  private static String asyncProfilerResultFilename(String name) {
    return Paths.get(getResultsDirName(), name + "-" + asyncProfilerEvent() + "-%p-%t.svg")
        .toString();
  }

  private static String getResultsDirName() {
    return System.getProperty(RESULTS_DIR_PROP_NAME, DEFAULT_RESULTS_DIR);
  }

  private static String asyncProfilerEvent() {
    return System.getProperty(ASYNC_PROFILER_EVENT_PROP_NAME, DEFAULT_ASYNC_PROFILER_EVENT);
  }

  /**
   * Returns jvmArgs.
   *
   * @return jvmArgs
   */
  public static String[] jvmArgs() {
    String value = System.getenv("JAVA_OPTS");
    if (value == null) {
      value = System.getProperty(JVM_ARGS_PROP_NAME, "");
    }
    return Arrays.stream(value.split("\\s")).filter(s -> !s.isEmpty()).toArray(String[]::new);
  }

  /**
   * Returns seed members.
   *
   * @return seed members set
   */
  public static List<Address> seedMembers() {
    return Arrays.stream(System.getProperty(SEEDS_PROPERTY, DEFAULT_SEEDS).split(","))
        .map(Address::from)
        .collect(Collectors.toList());
  }
}
