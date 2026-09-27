package com.delivery.platform.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

class CommonKafkaErrorHandlerTest {

    @Test
    void usesOneTwoAndFourSecondBackoffForThreeRetries() {
        CommonKafkaErrorHandler handler = new CommonKafkaErrorHandler(new TestKafkaTemplate(false));
        BackOffExecution execution = handler.retryBackOff().start();

        assertThat(execution.nextBackOff()).isEqualTo(1_000L);
        assertThat(execution.nextBackOff()).isEqualTo(2_000L);
        assertThat(execution.nextBackOff()).isEqualTo(4_000L);
        assertThat(execution.nextBackOff()).isEqualTo(BackOffExecution.STOP);
        assertThat(handler.isAckAfterHandle()).isTrue();
    }

    @Test
    void routesRecoveredRecordToSamePartitionDeadLetterTopic() {
        TestKafkaTemplate template = new TestKafkaTemplate(false);
        CommonKafkaErrorHandler handler = new CommonKafkaErrorHandler(template);

        handler.deadLetterRecoverer().accept(
                new ConsumerRecord<>("order.created", 2, 11L, "order-1", "payload"),
                new IllegalStateException("poison"));

        assertThat(template.lastRecord().topic()).isEqualTo("order.created.DLT");
        assertThat(template.lastRecord().partition()).isEqualTo(2);
        assertThat(CommonKafkaErrorHandler.deadLetterTopic("order.created"))
                .isEqualTo("order.created.DLT");
    }
}
