package com.delivery.settlement.application.cod;

import com.delivery.settlement.application.api.cod.*;
import com.delivery.settlement.domain.EntityType;
import com.delivery.settlement.domain.cod.*;
import com.delivery.settlement.domain.ledger.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefaultCodCapacityUseCaseTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final LocalDateTime now = LocalDateTime.now(clock);
    private final Store store = new Store();
    private final CodCapacityUseCase core = new DefaultCodCapacityUseCase(store, clock, UUID::randomUUID);
    private CodHoldCommand command(BigDecimal amount, LocalDateTime expiry) {
        return new CodHoldCommand(UUID.randomUUID(), 22L, UUID.randomUUID(), null,
                List.of(new CodHoldCommand.Item(null, UUID.randomUUID(), 1L, 2L, amount, expiry)));
    }
    @Test void replayDoesNotReserveTwiceAndReleaseIsIdempotent() {
        var command = command(BigDecimal.TEN, now.plusSeconds(30));
        var first = core.hold(command).get(0);
        assertEquals(new BigDecimal("10"), store.account.balance().reservedDeposit());
        assertEquals(first, core.hold(command).get(0));
        core.transition(first.holdId(), CodHold.Status.COMMITTED);
        assertEquals(new BigDecimal("10"), store.account.balance().reservedDeposit());
        core.transition(first.holdId(), CodHold.Status.RELEASED);
        core.transition(first.holdId(), CodHold.Status.RELEASED);
        assertEquals(BigDecimal.ZERO, store.account.balance().reservedDeposit());
        assertThrows(IllegalStateException.class, () -> core.hold(command));
    }
    @Test void expiredCommitAndSweepReleaseCapacityAndUseTheExistingScanBound() {
        var first = core.hold(command(BigDecimal.TEN, now)).get(0);
        assertEquals(CodHold.Status.EXPIRED, core.transition(first.holdId(), CodHold.Status.COMMITTED).status());
        var second = core.hold(command(BigDecimal.TEN, now.minusSeconds(1))).get(0);
        core.expireDueHolds();
        assertEquals(CodHold.Status.EXPIRED, store.holds.get(second.holdId()).status());
        assertEquals(200, store.scanLimit);
        assertEquals(BigDecimal.ZERO, store.account.balance().reservedDeposit());
    }
    @Test void deliveryCompletionConsumesHeldAndCommittedWithoutDebitingDeposit() {
        var held = core.hold(command(BigDecimal.TEN, now)).get(0);
        var committed = core.hold(command(BigDecimal.TEN, now.plusSeconds(10))).get(0);
        core.transition(committed.holdId(), CodHold.Status.COMMITTED);
        core.consumeForDelivery(null);
        core.consumeForDelivery(0L);
        core.consumeForDelivery(2L);
        assertEquals(CodHold.Status.CONSUMED, store.holds.get(held.holdId()).status());
        assertEquals(CodHold.Status.CONSUMED, store.holds.get(committed.holdId()).status());
        assertNotNull(store.holds.get(held.holdId()).committedAt());
        assertNotNull(store.holds.get(held.holdId()).consumedAt());
        assertEquals(BigDecimal.ZERO, store.account.balance().reservedDeposit());
        assertEquals(new BigDecimal("100"), store.account.balance().deposit());
    }
    @Test void providedHoldIdentityAndMixedReplayRetainOriginalReservationAndResultOrder() {
        var original = command(BigDecimal.TEN, now.plusSeconds(10));
        var first = core.hold(original).get(0);
        var id = UUID.randomUUID();
        var added = new CodHoldCommand.Item(id, UUID.randomUUID(), 3L, 4L, BigDecimal.TEN, now.plusSeconds(10));
        var result = core.hold(new CodHoldCommand(original.eventId(), 22L, original.matchingSessionId(), original.waveId(),
                List.of(added, original.offers().get(0))));
        assertEquals(first, result.get(0));
        assertEquals(id, result.get(1).holdId());
        assertEquals(new BigDecimal("20"), store.account.balance().reservedDeposit());
        core.consumeForDelivery(2L);
        core.consumeForDelivery(2L); // Includes the already-consumed projection from this fake store.
    }
    @Test void insufficientOrMissingCapacityAndMissingHoldFailWithoutSuccess() {
        assertThrows(InsufficientWalletFunds.class, () -> core.hold(command(new BigDecimal("101"), now)));
        assertTrue(store.holds.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> core.transition(UUID.randomUUID(), CodHold.Status.COMMITTED));
        assertThrows(IllegalArgumentException.class, () -> core.hold(null));
        var hold = core.hold(command(BigDecimal.TEN, now.plusSeconds(10))).get(0);
        store.account = null;
        assertThrows(IllegalStateException.class, () -> core.transition(hold.holdId(), CodHold.Status.RELEASED));
        assertThrows(InsufficientWalletFunds.class, () -> core.hold(command(BigDecimal.ONE, now)));
    }
    private final class Store implements CodCapacityStore {
        LedgerAccount account = new LedgerAccount(1L, new LedgerOwner(22L, EntityType.SHIPPER),
                WalletBalance.zero().topUp(new BigDecimal("100")));
        Map<UUID, CodHold> holds = new LinkedHashMap<>();
        int scanLimit;
        public Optional<LedgerAccount> lockShipper(Long shipperId) { return Optional.ofNullable(account); }
        public void saveReservedCapacity(LedgerAccount next) { account = next; }
        public Optional<CodHold> lockByKey(String key) { return holds.values().stream().filter(h -> h.idempotencyKey().equals(key)).findFirst(); }
        public Optional<CodHold> lockHold(UUID id) { return Optional.ofNullable(holds.get(id)); }
        public CodHold saveHold(CodHold hold) { holds.put(hold.holdId(), hold); return hold; }
        public List<CodHold> lockActiveForDelivery(Long id) { return holds.values().stream().filter(h -> h.deliveryId().equals(id)).toList(); }
        public List<CodHold> lockExpired(LocalDateTime at, int limit) {
            scanLimit = limit;
            return holds.values().stream().filter(h -> h.status() == CodHold.Status.HELD && !h.expiresAt().isAfter(at)).toList();
        }
    }
}
