package com.delivery.settlement_service.adapter;

import com.delivery.settlement.application.api.ledger.CodSettlementPort;
import com.delivery.settlement.domain.ledger.CompletedCodDelivery;
import com.delivery.settlement.domain.ledger.LedgerPosting;
import com.delivery.settlement_service.entity.EntityType;
import com.delivery.settlement_service.entity.SettlementReceipt;
import com.delivery.settlement_service.entity.Transaction.TransactionDirection;
import com.delivery.settlement_service.entity.Transaction.TransactionReason;
import com.delivery.settlement_service.entity.Transaction.WalletType;
import com.delivery.settlement_service.repository.SettlementReceiptRepository;
import com.delivery.settlement_service.repository.TransactionRepository;
import com.delivery.settlement_service.service.CodCapacityHoldService;
import com.delivery.settlement_service.service.TransactionService;
import java.util.Optional;
import java.util.UUID;

/** SQL/mapping bridge; every call joins the caller's existing financial transaction. */
public final class JpaCodSettlementAdapter implements CodSettlementPort {
    private final TransactionService transactions;
    private final TransactionRepository ledger;
    private final SettlementReceiptRepository receipts;
    private final CodCapacityHoldService holds;
    private final String dataSourceUrl;

    public JpaCodSettlementAdapter(TransactionService transactions, TransactionRepository ledger,
            SettlementReceiptRepository receipts, CodCapacityHoldService holds, String dataSourceUrl) {
        this.transactions = transactions;
        this.ledger = ledger;
        this.receipts = receipts;
        this.holds = holds;
        this.dataSourceUrl = dataSourceUrl;
    }

    @Override public Optional<Receipt> findByEventId(UUID eventId) {
        return receipts.findById(eventId).map(this::snapshot);
    }

    @Override public Optional<Receipt> findByOrderId(Long orderId) {
        return receipts.findByOrderId(orderId).map(this::snapshot);
    }

    @Override public boolean claimReceipt(CompletedCodDelivery delivery, String fingerprint) {
        String url = dataSourceUrl;
        // SQL uniqueness resolves concurrent claims inside the same transaction as all postings.
        return (url != null && url.startsWith("jdbc:h2:")
                ? receipts.insertIfAbsentH2(delivery.eventId(), delivery.orderId(), delivery.deliveryId(), fingerprint)
                : receipts.insertIfAbsentPostgres(delivery.eventId(), delivery.orderId(), delivery.deliveryId(), fingerprint)) == 1;
    }

    @Override public boolean hasUnreceiptedLedger(Long orderId) {
        return ledger.existsByOrderIdAndEntityIdAndEntityTypeAndReason(
                orderId, 0L, EntityType.SYSTEM, TransactionReason.PLATFORM_COMMISSION);
    }

    @Override public void post(LedgerPosting posting) {
        transactions.createTransaction(posting.entityId(), EntityType.valueOf(posting.entityType().name()),
                posting.orderId(), TransactionDirection.valueOf(posting.direction().name()),
                TransactionReason.valueOf(posting.reason().name()), posting.amount(), posting.description(),
                WalletType.valueOf(posting.wallet().name()));
    }

    @Override public void consumeCapacity(Long deliveryId) {
        // Null is supported only by the retained focused listener-test constructor.
        if (holds != null) holds.consumeForDelivery(deliveryId);
    }

    private Receipt snapshot(SettlementReceipt receipt) {
        return new Receipt(receipt.getEventId(), receipt.getOrderId(), receipt.getDeliveryId(), receipt.getPayloadFingerprint());
    }
}
