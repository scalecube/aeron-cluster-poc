package io.scalecube.acpoc.benchmarks.log4j2;

import org.apache.logging.log4j.core.LogEvent;

public interface AppenderAdapter {

  void append(LogEvent logEvent);
}
