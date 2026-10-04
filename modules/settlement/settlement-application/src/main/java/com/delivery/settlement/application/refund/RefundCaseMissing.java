package com.delivery.settlement.application.refund;

import java.util.UUID;

public final class RefundCaseMissing extends RuntimeException {
    private final UUID refundId;
    public RefundCaseMissing(UUID refundId) {super("Refund case not found");this.refundId=refundId;}
    public UUID refundId() {return refundId;}
}
