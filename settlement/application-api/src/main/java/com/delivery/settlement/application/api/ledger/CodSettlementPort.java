package com.delivery.settlement.application.api.ledger;

import com.delivery.settlement.domain.ledger.CompletedCodDelivery;
import com.delivery.settlement.domain.ledger.LedgerPosting;
import java.util.Optional;
import java.util.UUID;

/** All operations join one financial transaction, including the atomic receipt claim. */
public interface CodSettlementPort {
    record Receipt(UUID eventId, Long orderId, Long deliveryId, String fingerprint) {}
    Optional<Receipt> findByEventId(UUID eventId);
    Optional<Receipt> findByOrderId(Long orderId);
    /** Blocks on a competing claim; false requires re-reading the committed winner. */
    boolean claimReceipt(CompletedCodDelivery delivery, String fingerprint);
    boolean hasUnreceiptedLedger(Long orderId);
    void post(LedgerPosting posting);
    void consumeCapacity(Long deliveryId);
}
