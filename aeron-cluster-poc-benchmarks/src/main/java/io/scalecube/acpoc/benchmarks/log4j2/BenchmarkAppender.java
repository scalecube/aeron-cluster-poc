package io.scalecube.acpoc.benchmarks.log4j2;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;

public class BenchmarkAppender extends AbstractAppender {

  private final AppenderAdapter appender;

  BenchmarkAppender(AppenderAdapter appender) {
    super(BenchmarkAppender.class.getSimpleName(), null, null, true);
    this.appender = appender;
  }

  @Override
  public void append(LogEvent event) {
    appender.append(event);
  }
}
