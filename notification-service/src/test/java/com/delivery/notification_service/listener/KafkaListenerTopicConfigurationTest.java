package com.delivery.notification_service.listener;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;

import com.delivery.notification_service.exception.NotificationConflictException;

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
        assertNonBlockingRetry(OrderEventListener.class, "handleOrderCreatedEvent");
        assertNonBlockingRetry(DeliveryEventListener.class, "handleDeliveryStatusUpdatedEvent");
        assertNonBlockingRetry(MatchEventListener.class, "handleShipperFoundEvent");
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

    private void assertNonBlockingRetry(Class<?> listenerType, String methodName) {
        Method method = java.util.Arrays.stream(listenerType.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        RetryableTopic retry = method.getAnnotation(RetryableTopic.class);
        assertThat(retry).isNotNull();
        assertThat(retry.attempts()).isEqualTo("${app.kafka.retry.attempts:4}");
        assertThat(retry.autoCreateTopics()).isEqualTo("${app.kafka.retry.auto-create-topics:false}");
        assertThat(retry.exclude()).containsExactly(
                IllegalArgumentException.class, NotificationConflictException.class);
        assertThat(retry.kafkaTemplate()).isEqualTo("retryKafkaTemplate");
        assertThat(retry.retryTopicSuffix()).isEqualTo("-retry-notification");
        assertThat(retry.dltTopicSuffix()).isEqualTo(".notification.DLT");
    }
}
