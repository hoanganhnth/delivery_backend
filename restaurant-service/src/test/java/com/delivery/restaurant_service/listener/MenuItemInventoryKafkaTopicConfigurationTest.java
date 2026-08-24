package com.delivery.restaurant_service.listener;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.RetryableTopic;

import static org.assertj.core.api.Assertions.assertThat;

class MenuItemInventoryKafkaTopicConfigurationTest {

    @Test
    void inventoryConsumerUsesAnOwnerIsolatedRetryAndDltTopology() throws Exception {
        RetryableTopic retry = MenuItemInventoryOrderEventListener.class
                .getDeclaredMethod("consume", String.class, String.class,
                        org.springframework.kafka.support.Acknowledgment.class)
                .getAnnotation(RetryableTopic.class);

        assertThat(retry).isNotNull();
        assertThat(retry.kafkaTemplate()).isEqualTo("inventoryRetryKafkaTemplate");
        assertThat(retry.listenerContainerFactory()).isEqualTo("inventoryKafkaListenerContainerFactory");
        assertThat(retry.retryTopicSuffix()).isEqualTo("-retry-inventory");
        assertThat(retry.dltTopicSuffix()).isEqualTo(".inventory.DLT");
        assertThat(retry.autoCreateTopics()).isEqualTo("${app.kafka.retry.auto-create-topics:false}");
        assertThat(retry.exclude()).containsExactly(IllegalArgumentException.class);
    }
}
