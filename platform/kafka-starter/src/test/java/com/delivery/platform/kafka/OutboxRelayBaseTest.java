package com.delivery.platform.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class OutboxRelayBaseTest {

    @Test
    void marksEventOnlyAfterKafkaAcknowledgesIt() {
        TestKafkaTemplate template = new TestKafkaTemplate(false);
        TestRelay relay = new TestRelay(template, List.of(new TestEvent("one")));

        assertThat(relay.relayBatch(10)).isEqualTo(1);
        assertThat(relay.marked).containsExactly(new TestEvent("one"));
        assertThat(template.lastRecord().topic()).isEqualTo("order.created");
    }

    @Test
    void leavesEventPendingWhenKafkaPublishFails() {
        TestKafkaTemplate template = new TestKafkaTemplate(true);
        TestRelay relay = new TestRelay(template, List.of(new TestEvent("one")));

        assertThat(relay.relayBatch(10)).isZero();
        assertThat(relay.marked).isEmpty();
        assertThat(relay.failures).isEqualTo(1);
    }

    private static final class TestRelay extends OutboxRelayBase<TestEvent> {
        private final List<TestEvent> pending;
        private final java.util.ArrayList<TestEvent> marked = new java.util.ArrayList<>();
        private int failures;

        private TestRelay(KafkaTemplate<String, Object> kafkaTemplate, List<TestEvent> pending) {
            super(kafkaTemplate);
            this.pending = pending;
        }

        @Override
        protected List<TestEvent> pollPending(int batchSize) {
            return pending;
        }

        @Override
        protected ProducerRecord<String, Object> toProducerRecord(TestEvent event) {
            return new ProducerRecord<>("order.created", event.id(), event.id());
        }

        @Override
        protected void markPublished(TestEvent event) {
            marked.add(event);
        }

        @Override
        protected void onPublishFailure(TestEvent event, Exception failure) {
            failures++;
        }
    }

    private record TestEvent(String id) {
    }
}
