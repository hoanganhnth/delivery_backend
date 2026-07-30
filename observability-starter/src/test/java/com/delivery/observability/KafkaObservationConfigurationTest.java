package com.delivery.observability;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.env.Environment;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.support.ScheduledTaskObservationContext;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KafkaObservationConfigurationTest {
    @Test
    void enablesObservationForCustomKafkaTemplateAndListenerFactory() {
        BeanPostProcessor processor = processor(new MockEnvironment());
        KafkaTemplate<String, String> template = new KafkaTemplate<>(mock(ProducerFactory.class));
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();

        processor.postProcessAfterInitialization(template, "kafkaTemplate");
        processor.postProcessAfterInitialization(factory, "kafkaListenerContainerFactory");

        assertThat(factory.getContainerProperties().isObservationEnabled()).isTrue();
    }

    @Test
    void preservesTheTrackingServiceOptOut() {
        Environment environment = new MockEnvironment()
                .withProperty("app.observability.kafka-observation-enabled", "false");
        BeanPostProcessor processor = processor(environment);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);

        processor.postProcessAfterInitialization(template, "kafkaTemplate");

        verify(template, org.mockito.Mockito.never()).setObservationEnabled(true);
    }

    @Test
    void suppressesAutomaticScheduledTaskObservations() throws Exception {
        ObservationRegistry registry = ObservationRegistry.create();
        new ObservabilityAutoConfiguration().scheduledObservationSuppressor()
                .postProcessAfterInitialization(registry, "observationRegistry");
        Method method = KafkaObservationConfigurationTest.class.getDeclaredMethod("marker");
        Observation observation = Observation.createNotStarted("task marker",
                () -> new ScheduledTaskObservationContext(this, method), registry);

        assertThat(observation.isNoop()).isTrue();
    }

    private void marker() { }

    private BeanPostProcessor processor(Environment environment) {
        return new ObservabilityAutoConfiguration().correlationKafkaContextConfigurer(environment);
    }
}
