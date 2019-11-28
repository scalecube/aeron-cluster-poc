package io.scalecube.acpoc.benchmarks.log4j2;

import io.scalecube.acpoc.benchmarks.FooRequest;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.slf4j.Logger;

public abstract class LoggerBenchmarkSharedState {

  FooRequest fooRequest = FooRequest.newFooRequest();
  Logger logger;
  LoggerContext loggerContext;
  org.apache.logging.log4j.core.Logger rootLogger;

  void setUp() {
    loggerContext = createLoggerContext();
    rootLogger = loggerContext.getRootLogger();
  }

  Appender getAppenderByName(String name) {
    return rootLogger.getAppenders().values().stream()
        .filter(a -> a.getName().equals(name))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("Can't find existing appender to wrap"));
  }

  abstract LoggerContext createLoggerContext();

  void tearDown() {
    if (loggerContext != null) {
      loggerContext.stop();
    }
  }
}
