package com.delivery.notification_service.listener;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaListenerTopicConfigurationTest {

    @Test
    void listenerTopicsKeepCanonicalDefaultsAndAllowRuntimeOverrides() throws Exception {
        assertTopic(OrderEventListener.class, "handleOrderCreatedEvent",
                "${app.kafka.topics.order-created:order.created}");
        assertTopic(DeliveryEventListener.class, "handleDeliveryStatusUpdatedEvent",
                "${app.kafka.topics.delivery-status-updated:delivery.status-updated}");
        assertTopic(MatchEventListener.class, "handleShipperFoundEvent",
                "${app.kafka.topics.shipper-offered:delivery.shipper-offered}");
    }

    private void assertTopic(Class<?> listenerType, String methodName, String expected) {
        Method method = java.util.Arrays.stream(listenerType.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        KafkaListener annotation = method.getAnnotation(KafkaListener.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.topics()).containsExactly(expected);
    }
}
