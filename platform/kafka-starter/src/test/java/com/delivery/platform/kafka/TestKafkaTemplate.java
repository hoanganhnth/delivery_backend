package com.delivery.platform.kafka;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.support.serializer.JsonSerializer;

final class TestKafkaTemplate extends KafkaTemplate<String, Object> {

    private final boolean fail;
    private ProducerRecord<String, Object> lastRecord;

    TestKafkaTemplate(boolean fail) {
        super(new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class)));
        this.fail = fail;
    }

    @Override
    public CompletableFuture<SendResult<String, Object>> send(ProducerRecord<String, Object> record) {
        lastRecord = record;
        if (fail) {
            return CompletableFuture.failedFuture(new IllegalStateException("broker unavailable"));
        }
        return CompletableFuture.completedFuture(null);
    }

    ProducerRecord<String, Object> lastRecord() {
        return lastRecord;
    }
}
