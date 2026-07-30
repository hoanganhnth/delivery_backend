package com.delivery.observability;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/** Supplies one JSON console format unless a deployment explicitly overrides it. */
public final class StructuredLoggingEnvironmentPostProcessor implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getProperty("logging.structured.format.console") == null) {
            environment.getPropertySources().addLast(new MapPropertySource("deliveryStructuredLoggingDefaults",
                    Map.of("logging.structured.format.console", "logstash")));
        }
    }
}
