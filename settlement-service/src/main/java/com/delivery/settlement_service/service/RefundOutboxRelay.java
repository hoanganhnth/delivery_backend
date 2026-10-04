package com.delivery.settlement_service.service;

import com.delivery.settlement.application.refund.DefaultRefundOutboxRelayUseCase;
import com.delivery.settlement_service.adapter.JpaRefundOutboxRelayAdapter;
import com.delivery.settlement_service.adapter.KafkaRefundOutboxPublisher;
import com.delivery.settlement_service.repository.RefundOutboxEventRepository;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.refund.outbox-relay-enabled", havingValue = "true")
public class RefundOutboxRelay {
    private final RefundOutboxEventRepository repository;
    private final KafkaTemplate<String,String> kafka;
    public RefundOutboxRelay(RefundOutboxEventRepository repository, KafkaTemplate<String,String> kafka) {
        this.repository = repository;
        this.kafka = kafka;
    }
    @Scheduled(fixedDelayString = "${app.refund.outbox-relay-ms:1000}")
    @Transactional
    public void relay() {
        new DefaultRefundOutboxRelayUseCase(new JpaRefundOutboxRelayAdapter(repository),
                new KafkaRefundOutboxPublisher(kafka),Clock.systemDefaultZone()).relay();
    }
}
