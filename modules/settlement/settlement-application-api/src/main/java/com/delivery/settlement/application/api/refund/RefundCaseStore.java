package com.delivery.settlement.application.api.refund;

import com.delivery.settlement.domain.refund.*;
import java.util.Optional;
import java.util.UUID;

public interface RefundCaseStore {
    Optional<RefundReceipt> findByEvent(UUID eventId);
    Optional<RefundReceipt> findByKey(String key);
    Optional<RefundReceipt> findByOrder(Long orderId, RefundPolicy.Trigger trigger);
    boolean claim(RefundDraft draft);
    void enqueue(RefundDraft draft);
}
