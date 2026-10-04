package com.delivery.settlement.application.ledger;

import com.delivery.settlement.application.api.ledger.CodSettlementPort;
import com.delivery.settlement.application.api.ledger.CodSettlementUseCase;
import com.delivery.settlement.domain.ledger.CompletedCodDelivery;
import java.util.Objects;

/** Actual completion orchestration; the adapter supplies one atomic financial transaction. */
public final class DefaultCodSettlementUseCase implements CodSettlementUseCase {
    private final CodSettlementPort store;

    public DefaultCodSettlementUseCase(CodSettlementPort store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public Outcome settle(CompletedCodDelivery delivery, String fingerprint) {
        var plan = delivery.plan();
        var byEvent = store.findByEventId(delivery.eventId());
        if (byEvent.isPresent()) {
            requireMatchingReceipt(byEvent.get(), delivery, fingerprint);
            return Outcome.REPLAY;
        }
        var byOrder = store.findByOrderId(delivery.orderId());
        if (byOrder.isPresent()) {
            throw new IllegalArgumentException("order already settled by a different event: " + byOrder.get().eventId());
        }
        if (!store.claimReceipt(delivery, fingerprint)) {
            var winner = store.findByEventId(delivery.eventId()).orElseThrow(() -> new IllegalStateException(
                    "settlement receipt conflict resolved without a committed receipt"));
            requireMatchingReceipt(winner, delivery, fingerprint);
            return Outcome.REPLAY;
        }
        if (store.hasUnreceiptedLedger(delivery.orderId())) {
            throw new IllegalStateException("settlement ledger exists without a durable event receipt");
        }
        plan.beforeHoldConsumption().forEach(store::post);
        store.consumeCapacity(delivery.deliveryId());
        store.post(plan.platformCommission());
        return Outcome.POSTED;
    }

    private void requireMatchingReceipt(CodSettlementPort.Receipt receipt, CompletedCodDelivery delivery,
            String fingerprint) {
        if (!receipt.orderId().equals(delivery.orderId()) || !receipt.deliveryId().equals(delivery.deliveryId())
                || !receipt.fingerprint().equals(fingerprint)) {
            throw new IllegalArgumentException("eventId replay has a contradictory settlement payload");
        }
    }
}
