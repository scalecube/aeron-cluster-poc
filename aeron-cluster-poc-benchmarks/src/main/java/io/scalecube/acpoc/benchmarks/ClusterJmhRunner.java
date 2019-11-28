package io.scalecube.acpoc.benchmarks;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.VerboseMode;

public class ClusterJmhRunner {

  /**
   * Main method.
   *
   * @param args args
   * @throws RunnerException runner exception
   */
  public static void main(String[] args) throws RunnerException {
    OptionsBuilder optionsBuilder = new OptionsBuilder();
    if (Runners.asyncProfilerEnabled()) {
      optionsBuilder.jvmArgsPrepend(Runners.asyncProfilerAgentString(ClusterJmhRunner.class));
    }
    Options options =
        optionsBuilder
            .forks(Runners.forks())
            .jvmArgsAppend(Runners.jvmArgs())
            .threads(1)
            .verbosity(VerboseMode.NORMAL)
            .warmupIterations(Runners.warmupIterations())
            .warmupTime(Runners.warmupTime())
            .measurementIterations(Runners.measurementIterations())
            .measurementTime(Runners.measurementTime())
            .result(Runners.resultFilename(ClusterJmhRunner.class))
            .include(Runners.includeBenchmarks("acpoc.benchmarks.*.*Benchmark"))
            .shouldFailOnError(true)
            .build();
    new Runner(options).run();
  }
}
