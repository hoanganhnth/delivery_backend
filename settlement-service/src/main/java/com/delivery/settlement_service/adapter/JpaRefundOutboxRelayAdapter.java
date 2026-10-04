package com.delivery.settlement_service.adapter;

import com.delivery.settlement.application.api.refund.RefundOutboxRelayPort;
import com.delivery.settlement.domain.refund.*;
import com.delivery.settlement_service.entity.RefundOutboxEvent;
import com.delivery.settlement_service.repository.RefundOutboxEventRepository;
import java.time.LocalDateTime;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;

/** Invocation-local managed rows retain the original pessimistic transaction scope. */
@Slf4j
public final class JpaRefundOutboxRelayAdapter implements RefundOutboxRelayPort {
    private final RefundOutboxEventRepository repository;
    private final Map<UUID,RefundOutboxEvent> rows = new HashMap<>();
    public JpaRefundOutboxRelayAdapter(RefundOutboxEventRepository repository) { this.repository = repository; }
    @Override public List<RefundOutboxDelivery> lockDue(LocalDateTime now, int limit) {
        return repository.lockDue(RefundOutboxEvent.Status.PENDING, now, PageRequest.of(0,limit)).stream().map(row -> {
            rows.put(row.getEventId(),row);
            return new RefundOutboxDelivery(row.getEventId(),row.getTopic(),row.getEventKey(),row.getPayload(),
                    row.getAttempts(),row.getNextAttemptAt());
        }).toList();
    }
    @Override public void sent(UUID id, LocalDateTime at) {
        var row = requireRow(id);
        row.setStatus(RefundOutboxEvent.Status.SENT);
        row.setSentAt(at);
        row.setLastError(null);
    }
    @Override public void failed(UUID id, RefundOutboxFailure decision, Exception cause) {
        var row = requireRow(id);
        row.setAttempts(decision.attempts());
        row.setLastError(decision.lastError());
        if (decision.dead()) {
            row.setStatus(RefundOutboxEvent.Status.DEAD);
            log.error("Refund outbox {} DEAD", id, cause);
        } else row.setNextAttemptAt(decision.nextAttemptAt());
    }
    private RefundOutboxEvent requireRow(UUID id) { return Objects.requireNonNull(rows.get(id),"Outbox row is not locked"); }
}
