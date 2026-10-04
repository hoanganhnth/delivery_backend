package com.delivery.settlement_service.adapter;

import com.delivery.settlement.application.api.refund.RefundOutboxPublisher;
import com.delivery.settlement.domain.refund.RefundOutboxDelivery;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;

public final class KafkaRefundOutboxPublisher implements RefundOutboxPublisher {
    private final KafkaTemplate<String,String> kafka;
    public KafkaRefundOutboxPublisher(KafkaTemplate<String,String> kafka) { this.kafka = kafka; }
    @Override public void publish(RefundOutboxDelivery row) throws Exception {
        kafka.send(row.topic(),row.eventKey(),row.payload()).get(10,TimeUnit.SECONDS);
    }
}
