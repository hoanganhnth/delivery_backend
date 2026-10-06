package com.delivery.analytics.domain;

import java.math.BigDecimal;

/** Existing payment counter arithmetic, including unchecked long overflow. */
public record PaymentProjection(long successful, long failed, BigDecimal amount) {
    public PaymentProjection completed(BigDecimal increment) {
        return new PaymentProjection(successful + 1, failed, amount.add(increment));
    }

    public PaymentProjection failure() {
        return new PaymentProjection(successful, failed + 1, amount);
    }
}
