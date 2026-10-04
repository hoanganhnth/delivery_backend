package com.delivery.settlement.domain.ledger;

import java.time.LocalDateTime;

/** Ledger content plus the existing mutable processing status of gated operations. */
public record LedgerEntry(Long id, LedgerPosting posting, Status status, LocalDateTime processedAt,
        Long processedBy, LocalDateTime createdAt) {
    public enum Status { PENDING, COMPLETED, FAILED, REVERSED }

    public void requirePendingWithdrawal() {
        if (status != Status.PENDING) throw new IllegalStateException("Transaction is not pending: " + status);
        if (posting.reason() != LedgerPosting.Reason.WITHDRAW) {
            throw new IllegalStateException("Transaction is not a withdrawal: " + posting.reason());
        }
    }

    public void requireReversible() {
        if (status != Status.COMPLETED) throw new IllegalStateException("Can only reverse completed transactions");
    }

    public LedgerEntry processed(Status next, LocalDateTime now, Long admin, String description) {
        var updated = new LedgerPosting(posting.entityId(), posting.entityType(), posting.orderId(), posting.direction(),
                posting.reason(), posting.amount(), description, posting.wallet());
        return new LedgerEntry(id, updated, next, now, admin, createdAt);
    }

    public LedgerEntry reversed() { return new LedgerEntry(id, posting, Status.REVERSED, processedAt, processedBy, createdAt); }
    public LedgerOwner owner() { return new LedgerOwner(posting.entityId(), posting.entityType()); }
}
