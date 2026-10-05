package com.delivery.notification_service.config;

import com.delivery.delivery.contracts.ShipperFoundEvent;
import com.delivery.notification_service.listener.DeliveryEventListener;
import com.delivery.notification_service.listener.MatchEventListener;
import com.delivery.notification_service.listener.OrderEventListener;
import com.delivery.order.contracts.OrderCreatedEvent;
import com.delivery.platform.kafka.CommonKafkaConsumerConfig;
import com.delivery.platform.kafka.CommonKafkaErrorHandler;
import com.delivery.platform.kafka.CommonKafkaProducerConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.JacksonUtils;
import org.springframework.kafka.support.converter.ConversionException;
import org.springframework.kafka.support.serializer.JsonSerializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationKafkaConsumerConfigTest {

    private final NotificationKafkaConsumerConfig config = new NotificationKafkaConsumerConfig();
    private final ObjectMapper mapper = JacksonUtils.enhancedObjectMapper();
    private final UUID eventId = UUID.fromString("f498df81-2c52-4d59-a2b4-b8b77c2a8024");

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void deliveryListenerReceivesRawJsonWithRelayHeaderOrWithoutHeaders(boolean withHeader) throws Exception {
        String json = "{\"eventId\":\"" + eventId + "\",\"deliveryId\":2,\"orderId\":3,"
                + "\"userId\":7,\"status\":\"DELIVERING\"}";
        ConsumerRecord<String, String> record = wireRecord(mapper.readTree(json), withHeader);

        assertThat(convert(record, DeliveryEventListener.class, "handleDeliveryStatusUpdatedEvent"))
                .isEqualTo(json);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void matchListenerBindsObjectNodeRelayOrHeaderlessJsonToItsContract(boolean withHeader) throws Exception {
        String json = "{\"eventId\":\"" + eventId + "\",\"deliveryId\":2,\"orderId\":3,"
                + "\"restaurantName\":\"Restaurant A\",\"pickupAddress\":\"Pickup\","
                + "\"deliveryAddress\":\"Destination\","
                + "\"availableShippers\":[{\"shipperId\":8,\"distanceKm\":1.5}]}";

        Object result = convert(wireRecord(mapper.readTree(json), withHeader),
                MatchEventListener.class, "handleShipperFoundEvent");

        assertThat(result).isEqualTo(mapper.readValue(json, ShipperFoundEvent.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void orderListenerBindsTypedRelayOrHeaderlessJsonToItsContract(boolean withHeader) throws Exception {
        OrderCreatedEvent event = mapper.readValue("{\"eventId\":\"" + eventId + "\","
                + "\"orderId\":3,\"userId\":7,\"restaurantName\":\"Restaurant A\"}", OrderCreatedEvent.class);

        assertThat(convert(wireRecord(event, withHeader), OrderEventListener.class, "handleOrderCreatedEvent"))
                .isEqualTo(event);
    }

    @Test
    void inferredListenerTypeIgnoresEvenAnUnresolvableProducerTypeHeader() throws Exception {
        ConsumerRecord<String, String> record = wireRecord(mapper.readTree("{\"orderId\":3}"), false);
        record.headers().add("__TypeId__", "unavailable.producer.Class".getBytes(StandardCharsets.UTF_8));

        assertThat(convert(record, OrderEventListener.class, "handleOrderCreatedEvent"))
                .isInstanceOf(OrderCreatedEvent.class);
    }

    @Test
    void malformedTypedPayloadStillFailsConversion() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("proof", 0, 0, "key", "{broken");

        assertThatThrownBy(() -> convert(record, MatchEventListener.class, "handleShipperFoundEvent"))
                .isInstanceOf(ConversionException.class);
        assertThatThrownBy(() -> convert(record, OrderEventListener.class, "handleOrderCreatedEvent"))
                .isInstanceOf(ConversionException.class);
        // Delivery retains its own JSON parsing and poison/retry classification.
        assertThat(convert(record, DeliveryEventListener.class, "handleDeliveryStatusUpdatedEvent"))
                .isEqualTo("{broken");
    }

    @Test
    void serviceOverridesSharedWiringAndPreservesRecoveryJsonBytes() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CommonKafkaProducerConfig.class,
                        CommonKafkaConsumerConfig.class, KafkaAutoConfiguration.class))
                .withUserConfiguration(NotificationKafkaConsumerConfig.class)
                .withPropertyValues("spring.kafka.listener.auto-startup=false",
                        "spring.kafka.consumer.group-id=notification-service-group")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(ConsumerFactory.class)
                            .hasSingleBean(CommonKafkaErrorHandler.class).hasSingleBean(KafkaTemplate.class);
                    ConsumerFactory<?, ?> consumer = context.getBean(ConsumerFactory.class);
                    assertThat(consumer.getConfigurationProperties())
                            .containsEntry(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class)
                            .containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "notification-service-group")
                            .containsEntry(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
                            .containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
                    ConcurrentKafkaListenerContainerFactory<?, ?> factory =
                            context.getBean(ConcurrentKafkaListenerContainerFactory.class);
                    assertThat(factory.getContainerProperties().getAckMode())
                            .isEqualTo(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
                    assertThat(factory.createContainer("proof").getConcurrency()).isEqualTo(1);
                    DefaultKafkaProducerFactory<?, ?> producer = context.getBean(DefaultKafkaProducerFactory.class);
                    assertThat(context.getBean(KafkaTemplate.class).getProducerFactory()).isSameAs(producer);
                    @SuppressWarnings("unchecked")
                    org.apache.kafka.common.serialization.Serializer<Object> serializer =
                            (org.apache.kafka.common.serialization.Serializer<Object>) producer.getValueSerializer();
                    String rawJson = "{\"orderId\":3}";
                    assertThat(serializer.serialize("proof.DLT", new RecordHeaders(), rawJson))
                            .isEqualTo(rawJson.getBytes(StandardCharsets.UTF_8));
                    assertThat(serializer.serialize("proof.DLT", new RecordHeaders(), mapper.readTree(rawJson)))
                            .isEqualTo(rawJson.getBytes(StandardCharsets.UTF_8));
                });
    }

    private ConsumerRecord<String, String> wireRecord(Object producerValue, boolean withHeader) {
        RecordHeaders headers = new RecordHeaders();
        byte[] bytes;
        try (JsonSerializer<Object> serializer = new JsonSerializer<>()) {
            bytes = serializer.serialize("proof", headers, producerValue);
        }
        assertThat(new String(headers.lastHeader("__TypeId__").value(), StandardCharsets.UTF_8))
                .isEqualTo(producerValue.getClass().getName());
        if (!withHeader) headers.remove("__TypeId__");
        try (StringDeserializer deserializer = new StringDeserializer()) {
            ConsumerRecord<String, String> record = new ConsumerRecord<>("proof", 0, 0, "key",
                    deserializer.deserialize("proof", headers, bytes));
            headers.forEach(record.headers()::add);
            return record;
        }
    }

    private Object convert(ConsumerRecord<String, String> record, Class<?> listener, String methodName) {
        Type payloadType = Arrays.stream(listener.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName)).findFirst().orElseThrow()
                .getGenericParameterTypes()[0];
        return config.notificationMessageConverter().toMessage(record, null, null, payloadType).getPayload();
    }
}
