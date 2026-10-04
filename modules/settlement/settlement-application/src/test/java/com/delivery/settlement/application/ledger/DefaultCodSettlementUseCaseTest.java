package com.delivery.settlement.application.ledger;

import com.delivery.settlement.application.api.ledger.CodSettlementPort;
import com.delivery.settlement.application.api.ledger.CodSettlementUseCase.Outcome;
import com.delivery.settlement.domain.ledger.CompletedCodDelivery;
import com.delivery.settlement.domain.ledger.LedgerPosting;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefaultCodSettlementUseCaseTest {
    private final CompletedCodDelivery delivery = new CompletedCodDelivery(UUID.randomUUID(),
            "DELIVERY_COMPLETED", 1L, 101L, 11L, 22L, "COD", new BigDecimal("80000"),
            new BigDecimal("17000"), new BigDecimal("20000"), new BigDecimal("3000"),
            new BigDecimal("23000"), new BigDecimal("20000"), null, null, null, null, null, null,
            new BigDecimal("120000"));

    @Test
    void postsOnceAndConsumesHoldBeforePlatformCommission() {
        var store = new Store();
        var core = new DefaultCodSettlementUseCase(store);
        assertEquals(Outcome.POSTED, core.settle(delivery, "wire-hash"));
        assertEquals(Outcome.REPLAY, core.settle(delivery, "wire-hash"));
        assertEquals(List.of("ORDER_EARNING", "DELIVERY_FEE", "COD_SETTLEMENT", "hold:1",
                "PLATFORM_COMMISSION"), store.effects);
    }

    @Test
    void contradictoryReplayAndDifferentEventForOrderNeverPost() {
        var store = new Store();
        store.receipt = new CodSettlementPort.Receipt(delivery.eventId(), 101L, 1L, "other-payload");
        assertThrows(IllegalArgumentException.class, () -> new DefaultCodSettlementUseCase(store).settle(delivery, "wire-hash"));
        store.receipt = new CodSettlementPort.Receipt(UUID.randomUUID(), 101L, 1L, "wire-hash");
        assertThrows(IllegalArgumentException.class, () -> new DefaultCodSettlementUseCase(store).settle(delivery, "wire-hash"));
        assertTrue(store.effects.isEmpty());
    }

    @Test
    void concurrentClaimWinnerIsReplayedAndMissingWinnerFailsClosed() {
        var store = new Store();
        store.claimWins = false;
        store.concurrentWinner = new CodSettlementPort.Receipt(delivery.eventId(), 101L, 1L, "wire-hash");
        assertEquals(Outcome.REPLAY, new DefaultCodSettlementUseCase(store).settle(delivery, "wire-hash"));
        assertTrue(store.effects.isEmpty());
        store.receipt = null;
        store.concurrentWinner = null;
        assertThrows(IllegalStateException.class, () -> new DefaultCodSettlementUseCase(store).settle(delivery, "wire-hash"));
    }

    @Test
    void unreceiptedLegacyLedgerAndFailedPostingCannotProduceSuccess() {
        var store = new Store();
        store.legacyLedger = true;
        assertThrows(IllegalStateException.class, () -> new DefaultCodSettlementUseCase(store).settle(delivery, "wire-hash"));
        assertTrue(store.effects.isEmpty());
        store.receipt = null;
        store.legacyLedger = false;
        store.failPosting = true;
        assertThrows(IllegalStateException.class, () -> new DefaultCodSettlementUseCase(store).settle(delivery, "wire-hash"));
        assertTrue(store.effects.isEmpty());
    }

    @Test
    void replayCannotChangeOrderOrDeliveryAndConcurrentPayloadConflictIsRejected() {
        var store = new Store();
        var core = new DefaultCodSettlementUseCase(store);
        store.receipt = new CodSettlementPort.Receipt(delivery.eventId(), 102L, 1L, "wire-hash");
        assertThrows(IllegalArgumentException.class, () -> core.settle(delivery, "wire-hash"));
        store.receipt = new CodSettlementPort.Receipt(delivery.eventId(), 101L, 2L, "wire-hash");
        assertThrows(IllegalArgumentException.class, () -> core.settle(delivery, "wire-hash"));
        store.receipt = null;
        store.claimWins = false;
        store.concurrentWinner = new CodSettlementPort.Receipt(delivery.eventId(), 101L, 1L, "other-payload");
        assertThrows(IllegalArgumentException.class, () -> core.settle(delivery, "wire-hash"));
        assertTrue(store.effects.isEmpty());
    }

    private static final class Store implements CodSettlementPort {
        Receipt receipt;
        Receipt concurrentWinner;
        boolean claimWins = true;
        boolean legacyLedger;
        boolean failPosting;
        List<String> effects = new ArrayList<>();
        public Optional<Receipt> findByEventId(UUID id) { return Optional.ofNullable(receipt).filter(r -> r.eventId().equals(id)); }
        public Optional<Receipt> findByOrderId(Long id) { return Optional.ofNullable(receipt).filter(r -> r.orderId().equals(id)); }
        public boolean claimReceipt(CompletedCodDelivery d, String hash) {
            receipt = claimWins ? new Receipt(d.eventId(), d.orderId(), d.deliveryId(), hash) : concurrentWinner;
            return claimWins;
        }
        public boolean hasUnreceiptedLedger(Long order) { return legacyLedger; }
        public void post(LedgerPosting posting) {
            if (failPosting) throw new IllegalStateException("database write failed");
            effects.add(posting.reason().name());
        }
        public void consumeCapacity(Long id) { effects.add("hold:" + id); }
    }
}
