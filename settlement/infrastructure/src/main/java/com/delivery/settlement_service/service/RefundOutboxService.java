package com.delivery.settlement_service.service;

import com.delivery.settlement.application.api.refund.RefundOutboxEnqueueUseCase;
import com.delivery.settlement.application.refund.DefaultRefundOutboxEnqueueUseCase;
import com.delivery.settlement.domain.refund.RefundOutboxRequest;
import com.delivery.settlement.domain.refund.RefundPolicy;
import com.delivery.settlement_service.adapter.JpaLedgerAdapter;
import com.delivery.settlement_service.adapter.JpaRefundOutboxWriter;
import com.delivery.settlement_service.entity.RefundCase;
import com.delivery.settlement_service.repository.RefundOutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefundOutboxService {
    private final RefundOutboxEnqueueUseCase core;
    public RefundOutboxService(RefundOutboxEventRepository repository, ObjectMapper mapper,
            @Value("${app.kafka.topics.refund-requested:refund.requested}") String topic) {
        core = new DefaultRefundOutboxEnqueueUseCase(new JpaRefundOutboxWriter(repository,mapper,topic),Clock.systemDefaultZone());
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueue(RefundCase row) {
        return core.enqueue(new RefundOutboxRequest(row.getRefundId(),row.getOrderId(),row.getRefundAmount(),row.getCurrency(),
                row.getPaymentMethod(),JpaLedgerAdapter.enumValue(row.getTrigger(),RefundPolicy.Trigger.class),
                JpaLedgerAdapter.enumValue(row.getStatus(),RefundPolicy.Status.class)));
    }
}
