package com.delivery.settlement.domain.refund;

import java.time.LocalDateTime;
import java.util.UUID;

public record RefundOutboxIntent(RefundOutboxRequest request,UUID eventId,String eventType,LocalDateTime occurredAt) {
    public String aggregateType() {return "REFUND_CASE";}
    public String aggregateId() {return request.refundId().toString();}
    public String eventKey() {return request.orderId().toString();}
}
