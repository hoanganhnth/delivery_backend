package com.delivery.settlement.domain.payment;

import com.delivery.settlement.domain.EntityType;
import java.util.Map;
import java.util.UUID;

/** Provider-neutral payout command for an already-authoritative ledger entry. */
public record PayoutRequest(
        UUID payoutId,
        String idempotencyKey,
        String merchantReference,
        EntityType beneficiaryType,
        Long beneficiaryId,
        MoneyAmount amount,
        String sourceLedgerReference,
        Map<String, String> metadata) {

    public PayoutRequest {
        if (payoutId == null) throw new IllegalArgumentException("payoutId is required");
        requireText(idempotencyKey, "idempotencyKey");
        requireText(merchantReference, "merchantReference");
        if (beneficiaryType == null || beneficiaryType == EntityType.SYSTEM) {
            throw new IllegalArgumentException("payout beneficiary must be RESTAURANT or SHIPPER");
        }
        if (beneficiaryId == null || beneficiaryId <= 0) {
            throw new IllegalArgumentException("beneficiaryId must be positive");
        }
        if (amount == null || !amount.isPositive()) {
            throw new IllegalArgumentException("payout amount must be positive");
        }
        requireText(sourceLedgerReference, "sourceLedgerReference");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }
}
