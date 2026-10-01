package com.delivery.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class Phase8MetricsConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ObservabilityAutoConfiguration.class));

    @Test
    void exposesServiceTaggedMetricsWhenRegistryIsAvailable() {
        runner.withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withPropertyValues("spring.application.name=search-service")
                .run(context -> {
                    assertThat(context).hasSingleBean(Phase8Metrics.class);
                    context.getBean(Phase8Metrics.class).staleEventRejected();
                    assertThat(context.getBean(MeterRegistry.class)
                            .counter("delivery.events", "service", "search-service", "outcome", "stale_rejected").count())
                            .isEqualTo(1);
                });
    }

    @Test
    void doesNotInventRegistryWhenMetricsAreUnavailable() {
        runner.run(context -> assertThat(context).doesNotHaveBean(Phase8Metrics.class));
    }

    @Test
    void preservesExplicitMetricsOverride() {
        var registry = new SimpleMeterRegistry();
        var override = new Phase8Metrics(registry, "custom");
        runner.withBean(MeterRegistry.class, () -> registry).withBean(Phase8Metrics.class, () -> override)
                .run(context -> assertThat(context.getBean(Phase8Metrics.class)).isSameAs(override));
    }
}
