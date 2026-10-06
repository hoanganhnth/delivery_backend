package com.delivery.promotion_service.listener;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaListenerTopicConfigurationTest {

    @Test
    void voucherReservationListenerUsesOwnerIsolatedRecoveryTopics() throws Exception {
        Method method = OrderReservationEventListener.class.getDeclaredMethod(
                "consume", String.class, String.class, org.springframework.kafka.support.Acknowledgment.class);
        KafkaListener listener = method.getAnnotation(KafkaListener.class);
        RetryableTopic retry = method.getAnnotation(RetryableTopic.class);

        assertThat(listener.topics()).containsExactly(
                "${app.kafka.topics.order-created:order.created}",
                "${app.kafka.topics.order-cancelled:order.cancelled}",
                "${app.kafka.topics.refund-eligible:order.refund-eligible}");
        assertThat(retry.attempts()).isEqualTo("${app.kafka.retry.attempts:4}");
        assertThat(retry.exclude()).containsExactly(IllegalArgumentException.class);
        assertThat(retry.kafkaTemplate()).isEqualTo("retryKafkaTemplate");
        assertThat(retry.retryTopicSuffix()).isEqualTo("-retry-promotion");
        assertThat(retry.dltTopicSuffix()).isEqualTo(".promotion.DLT");
        assertThat(retry.autoCreateTopics()).isEqualTo("${app.kafka.retry.auto-create-topics:false}");
    }
}
