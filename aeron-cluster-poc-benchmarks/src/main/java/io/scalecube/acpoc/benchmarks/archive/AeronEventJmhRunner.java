package io.scalecube.acpoc.benchmarks.archive;

import io.scalecube.acpoc.benchmarks.Runners;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import org.openjdk.jmh.runner.options.VerboseMode;

public class AeronEventJmhRunner {

  /**
   * Main method.
   *
   * @param args args
   * @throws RunnerException runner exception
   */
  public static void main(String[] args) throws RunnerException {
    OptionsBuilder optionsBuilder = new OptionsBuilder();
    if (Runners.asyncProfilerEnabled()) {
      optionsBuilder.jvmArgsPrepend(Runners.asyncProfilerAgentString(AeronEventJmhRunner.class));
    }
    Options options =
        optionsBuilder
            .forks(Runners.forks())
            .jvmArgsAppend(Runners.jvmArgs())
            .threads(1)
            .verbosity(VerboseMode.NORMAL)
            .warmupIterations(Runners.warmupIterations())
            .warmupTime(TimeValue.milliseconds(Runners.warmupTime().toMillis()))
            .measurementIterations(Runners.measurementIterations())
            .measurementTime(TimeValue.milliseconds(Runners.measurementTime().toMillis()))
            .result(Runners.resultFilename(AeronEventJmhRunner.class))
            .include(Runners.includeBenchmarks("acpoc.benchmarks.archive.*.*Benchmark"))
            .shouldFailOnError(true)
            .build();
    new Runner(options).run();
  }
}
