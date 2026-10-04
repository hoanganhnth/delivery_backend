package com.delivery.settlement.domain.refund;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public record RefundOutboxRequest(UUID refundId,Long orderId,BigDecimal amount,String currency,String paymentMethod,
        RefundPolicy.Trigger trigger,RefundPolicy.Status status) {
    public String eventType() {return "REFUND_"+status.name();}
    public UUID eventId() {return UUID.nameUUIDFromBytes((refundId+":"+eventType()).getBytes(StandardCharsets.UTF_8));}
}
