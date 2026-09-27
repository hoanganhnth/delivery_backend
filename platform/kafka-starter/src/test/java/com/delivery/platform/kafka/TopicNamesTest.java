package com.delivery.platform.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TopicNamesTest {

    @Test
    void preservesExistingDeliveryTopicPatterns() {
        assertThat(TopicNames.ORDER_CREATED).isEqualTo("order.created");
        assertThat(TopicNames.ORDER_CANCELLED).isEqualTo("order.cancelled");
        assertThat(TopicNames.DELIVERY_COMPLETED).isEqualTo("delivery.completed");
        assertThat(TopicNames.SHIPPER_LOCATION_UPDATED).isEqualTo("shipper.location-updated");
        assertThat(TopicNames.SAGA_COMMAND_FIND_SHIPPER).isEqualTo("saga.command.find-shipper");
        assertThat(TopicNames.ENTITY_SYNC).isEqualTo("entity-sync");
    }

    @Test
    void derivesConventionalDeadLetterTopic() {
        assertThat(TopicNames.deadLetterTopic(TopicNames.ORDER_CREATED))
                .isEqualTo("order.created.DLT");
    }
}
