package com.delivery.platform.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;

class CommonKafkaAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CommonKafkaProducerConfig.class,
                    CommonKafkaConsumerConfig.class,
                    KafkaAutoConfiguration.class));

    @Test
    void registersSharedKafkaBeansFromBootAutoConfiguration() {
        contextRunner
                .withPropertyValues(
                        "spring.kafka.bootstrap-servers=kafka:19092",
                        "spring.kafka.consumer.group-id=platform-test")
                .run(context -> {
                    assertThat(context).hasSingleBean(KafkaTemplate.class);
                    assertThat(context).hasSingleBean(ConcurrentKafkaListenerContainerFactory.class);
                    assertThat(context).hasSingleBean(CommonKafkaErrorHandler.class);
                    assertThat(context.getBean(CommonKafkaErrorHandler.class).dltSuffix())
                            .isEqualTo(CommonKafkaErrorHandler.DEFAULT_DLT_SUFFIX);
                });
    }
}
