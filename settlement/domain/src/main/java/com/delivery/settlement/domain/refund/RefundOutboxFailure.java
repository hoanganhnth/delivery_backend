package com.delivery.settlement.domain.refund;

import java.time.LocalDateTime;

/** Existing bounded Kafka outbox retry policy; no payment-provider execution. */
public record RefundOutboxFailure(int attempts,String lastError,boolean dead,LocalDateTime nextAttemptAt) {
    public static RefundOutboxFailure next(int previousAttempts,LocalDateTime previousRetry,LocalDateTime now,String message) {
        int attempts=previousAttempts+1;
        String error=message==null ? "Kafka publish failed" : message;
        error=error.substring(0,Math.min(2000,error.length()));
        boolean dead=attempts>=12;
        return new RefundOutboxFailure(attempts,error,dead,dead ? previousRetry
                : now.plusSeconds(Math.min(300,1L<<Math.min(attempts,8))));
    }
}
