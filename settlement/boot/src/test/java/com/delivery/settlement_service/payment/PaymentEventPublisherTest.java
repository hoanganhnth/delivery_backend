package com.delivery.settlement_service.payment;

import com.delivery.settlement_service.entity.*;
import com.delivery.settlement_service.service.PaymentEventPublisher;
import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.serialization.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(PaymentEventPublisherTest.Config.class)
@EmbeddedKafka(partitions = 1, topics = {"payment.completed", "payment.failed"})
@DirtiesContext
class PaymentEventPublisherTest {
    @Configuration static class Config {}
    @Autowired EmbeddedKafkaBroker broker;
    private PaymentOrder order() {
        return PaymentOrder.builder().id(7L).orderId(19L).entityId(23L).paymentRef("PAY-publisher")
                .provider("VNPAY").providerTransactionId("provider-transaction").amount(new BigDecimal("100.99")).build();
    }
    @Test void realKafkaTemplatePublishesExactTopicsKeysAndEveryPayloadField() throws Exception {
        var factory = new DefaultKafkaProducerFactory<String, Object>(KafkaTestUtils.producerProps(broker),
                new StringSerializer(), new JsonSerializer<>());
        var template = new KafkaTemplate<>(factory);
        var publisher = new PaymentEventPublisher(template);
        var consumerFactory = new DefaultKafkaConsumerFactory<String, String>(
                KafkaTestUtils.consumerProps("payment-publisher-proof", "false", broker), new StringDeserializer(), new StringDeserializer());
        try (Consumer<String, String> consumer = consumerFactory.createConsumer()) {
            broker.consumeFromEmbeddedTopics(consumer, "payment.completed", "payment.failed");
            var before = LocalDateTime.now();
            publisher.publishPaymentSuccess(order()); publisher.publishPaymentFailed(order(), "cancelled"); template.flush();
            var after = LocalDateTime.now();
            var records = KafkaTestUtils.getRecords(consumer, java.time.Duration.ofSeconds(10), 2);
            assertThat(records.count()).isEqualTo(2);
            var mapper = new ObjectMapper();
            for (var record : records) {
                boolean success = record.topic().equals("payment.completed");
                assertThat(record.topic()).isIn("payment.completed", "payment.failed");
                assertThat(record.key()).isEqualTo("PAY-publisher");
                JsonNode json = mapper.readTree(record.value());
                assertThat(json.size()).isEqualTo(9);
                assertThat(json.get("paymentId").asLong()).isEqualTo(7);
                assertThat(json.get("orderId").asLong()).isEqualTo(19);
                assertThat(json.get("userId").asLong()).isEqualTo(23);
                assertThat(json.get("status").asText()).isEqualTo(success ? "COMPLETED" : "FAILED");
                assertThat(json.get("amount").asDouble()).isEqualTo(100.99);
                assertThat(json.get("paymentMethod").asText()).isEqualTo("VNPAY");
                if (success) {
                    assertThat(json.get("transactionId").asText()).isEqualTo("provider-transaction");
                    assertThat(json.get("failureReason").isNull()).isTrue();
                } else {
                    assertThat(json.get("transactionId").isNull()).isTrue();
                    assertThat(json.get("failureReason").asText()).isEqualTo("cancelled");
                }
                var timestamp = json.get("processedAt");
                LocalDateTime processed = timestamp.isArray()
                        ? LocalDateTime.of(timestamp.get(0).asInt(), timestamp.get(1).asInt(), timestamp.get(2).asInt(),
                                timestamp.get(3).asInt(), timestamp.get(4).asInt(), timestamp.size() > 5 ? timestamp.get(5).asInt() : 0,
                                timestamp.size() > 6 ? timestamp.get(6).asInt() : 0)
                        : LocalDateTime.parse(timestamp.asText());
                assertThat(processed).isBetween(before, after);
            }
        } finally { template.destroy(); factory.destroy(); }
    }
    @Test void realTemplateSynchronousProducerFailureIsSwallowedForBothEvents() {
        ProducerFactory<String, Object> factory = mock(ProducerFactory.class);
        when(factory.createProducer()).thenThrow(new IllegalStateException("send unavailable"));
        var publisher = new PaymentEventPublisher(new KafkaTemplate<>(factory));
        assertThatCode(() -> publisher.publishPaymentSuccess(order())).doesNotThrowAnyException();
        assertThatCode(() -> publisher.publishPaymentFailed(order(), "cancelled")).doesNotThrowAnyException();
        verify(factory, times(2)).createProducer();
    }
    @Test void realTemplateExceptionalSendFuturesAreNotPropagated() {
        var producer = new MockProducer<String, Object>(false, new StringSerializer(), new JsonSerializer<>());
        ProducerFactory<String, Object> factory = mock(ProducerFactory.class);
        var failedProducer = new MockProducer<String, Object>(false, new StringSerializer(), new JsonSerializer<>());
        when(factory.createProducer()).thenReturn(producer, failedProducer);
        var publisher = new PaymentEventPublisher(new KafkaTemplate<>(factory));
        assertThatCode(() -> publisher.publishPaymentSuccess(order())).doesNotThrowAnyException();
        assertThat(producer.errorNext(new IllegalStateException("async success failure"))).isTrue();
        assertThatCode(() -> publisher.publishPaymentFailed(order(), "cancelled")).doesNotThrowAnyException();
        assertThat(failedProducer.errorNext(new IllegalStateException("async failed failure"))).isTrue();
        assertThat(producer.history()).extracting(record -> record.topic()).containsExactly("payment.completed");
        assertThat(failedProducer.history()).extracting(record -> record.topic()).containsExactly("payment.failed");
    }
}
