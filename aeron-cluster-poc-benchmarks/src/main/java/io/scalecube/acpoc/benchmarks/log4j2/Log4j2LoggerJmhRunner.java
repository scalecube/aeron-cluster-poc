package io.scalecube.acpoc.benchmarks.log4j2;

import io.scalecube.acpoc.benchmarks.Runners;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import org.openjdk.jmh.runner.options.VerboseMode;

public class Log4j2LoggerJmhRunner {

  /**
   * Main method.
   *
   * @param args args
   * @throws RunnerException runner exception
   */
  public static void main(String[] args) throws RunnerException {
    OptionsBuilder optionsBuilder = new OptionsBuilder();
    if (Runners.asyncProfilerEnabled()) {
      optionsBuilder.jvmArgsPrepend(Runners.asyncProfilerAgentString(Log4j2LoggerJmhRunner.class));
    }
    Options options =
        optionsBuilder
            .forks(Runners.forks())
            .jvmArgsAppend(Runners.jvmArgs())
            .threads(Runners.threads())
            .verbosity(VerboseMode.NORMAL)
            .warmupIterations(Runners.warmupIterations())
            .warmupTime(TimeValue.milliseconds(Runners.warmupTime().toMillis()))
            .measurementIterations(Runners.measurementIterations())
            .measurementTime(TimeValue.milliseconds(Runners.measurementTime().toMillis()))
            .result(Runners.resultFilename(Log4j2LoggerJmhRunner.class))
            .include(Runners.includeBenchmarks("benchmarks.log4j2.*.*Benchmark"))
            .shouldFailOnError(true)
            .build();
    new Runner(options).run();
  }
}
