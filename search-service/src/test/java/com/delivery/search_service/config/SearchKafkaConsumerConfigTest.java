package com.delivery.search_service.config;

import com.delivery.search_service.dto.EntitySyncEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class SearchKafkaConsumerConfigTest {

    @Test
    void consumesStructuredOutboxJsonRegardlessOfProducerTypeHeader() {
        SearchKafkaConsumerConfig config = new SearchKafkaConsumerConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
        ReflectionTestUtils.setField(config, "groupId", "search-service-group");

        var properties = config.consumerFactory().getConfigurationProperties();

        assertThat(properties)
                .containsEntry(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class)
                .containsEntry(JsonDeserializer.USE_TYPE_INFO_HEADERS, false)
                .containsEntry(JsonDeserializer.VALUE_DEFAULT_TYPE, EntitySyncEvent.class.getName());
    }
}
