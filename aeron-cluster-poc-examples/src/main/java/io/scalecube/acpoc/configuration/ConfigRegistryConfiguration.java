package io.scalecube.acpoc.configuration;

import io.scalecube.config.ConfigRegistry;
import io.scalecube.config.ConfigRegistrySettings;
import io.scalecube.config.audit.Slf4JConfigEventListener;
import io.scalecube.config.source.ClassPathConfigSource;
import io.scalecube.config.source.SystemEnvironmentConfigSource;
import io.scalecube.config.source.SystemPropertiesConfigSource;
import java.util.regex.Pattern;

public class ConfigRegistryConfiguration {

  private static final Pattern FILENAME_PATTERN = Pattern.compile("(.*)config(.*)?\\.properties");
  private static final int RELOAD_INTERVAL_SEC = 300;

  /**
   * Create a new configuration with default settings.
   *
   * @return a new default config registry for the exchange
   */
  public static ConfigRegistry configRegistry() {
    ConfigRegistrySettings.Builder builder =
        ConfigRegistrySettings.builder()
            .jmxEnabled(false)
            .reloadIntervalSec(RELOAD_INTERVAL_SEC)
            .addListener(new Slf4JConfigEventListener());

    builder
        .addLastSource("systemEnvironment", new SystemEnvironmentConfigSource())
        .addLastSource("systemProperties", new SystemPropertiesConfigSource())
        .addLastSource(
            "classpath",
            new ClassPathConfigSource(
                path -> FILENAME_PATTERN.matcher(path.getFileName().toString()).matches()));

    return ConfigRegistry.create(builder.build());
  }
}
