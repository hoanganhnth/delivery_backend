package com.delivery.settlement.application.cod;

import com.delivery.settlement.application.api.cod.*;
import com.delivery.settlement.domain.cod.*;
import com.delivery.settlement.domain.ledger.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;

public final class DefaultCodCapacityUseCase implements CodCapacityUseCase {
    private final CodCapacityStore store;
    private final Clock clock;
    private final Supplier<UUID> ids;
    public DefaultCodCapacityUseCase(CodCapacityStore store, Clock clock, Supplier<UUID> ids) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ids = Objects.requireNonNull(ids, "ids");
    }
    @Override public List<CodHold> hold(CodHoldCommand command) {
        if (command == null) throw new IllegalArgumentException("Invalid COD capacity hold request");
        command.validate();
        var items = command.offers().stream().sorted(Comparator.comparing(CodHoldCommand.Item::offerId)).toList();
        var account = store.lockShipper(command.shipperId()).orElseThrow(() -> new InsufficientWalletFunds("Shipper deposit balance not found"));
        var now = LocalDateTime.now(clock);
        var existing = new ArrayList<CodHold>();
        BigDecimal requested = BigDecimal.ZERO;
        for (var item : items) {
            var replay = store.lockByKey(command.idempotencyKey(item));
            if (replay.isPresent()) {
                replay.get().requireReusable(now);
                existing.add(replay.get());
            } else requested = requested.add(item.amount());
        }
        var reserved = account.balance().reserveCodCapacity(requested);
        var result = new ArrayList<>(existing);
        for (var item : items) {
            String key = command.idempotencyKey(item);
            if (store.lockByKey(key).isPresent()) continue;
            var hold = new CodHold(item.holdId() == null ? ids.get() : item.holdId(), item.offerId(), item.orderId(),
                    item.deliveryId(), command.shipperId(), command.matchingSessionId(), command.waveId(), item.amount(),
                    CodHold.Status.HELD, item.expiresAt(), command.eventId(), key, now, null, null, null);
            result.add(store.saveHold(hold));
        }
        store.saveReservedCapacity(account.withBalance(reserved));
        return result;
    }
    @Override public CodHold transition(UUID holdId, CodHold.Status target) {
        var hold = store.lockHold(holdId).orElseThrow(() -> new IllegalArgumentException("COD hold not found"));
        var decision = hold.decideTransition(target, LocalDateTime.now(clock));
        if (decision.unchanged()) return hold;
        var committed = hold.committedAt();
        var released = hold.releasedAt();
        var consumed = hold.consumedAt();
        if (decision.stamp() == CodHold.Stamp.CONSUMED) consumed = LocalDateTime.now(clock);
        if (decision.releaseReservation()) releaseCapacity(hold);
        if (decision.stamp() == CodHold.Stamp.RELEASED) released = LocalDateTime.now(clock);
        if (decision.stamp() == CodHold.Stamp.COMMITTED) committed = LocalDateTime.now(clock);
        return store.saveHold(hold.updated(decision.target(), committed, released, consumed));
    }
    @Override public void consumeForDelivery(Long deliveryId) {
        if (deliveryId == null || deliveryId <= 0) return;
        for (var hold : store.lockActiveForDelivery(deliveryId)) {
            if (hold.status() == CodHold.Status.HELD) {
                var committed = LocalDateTime.now(clock);
                releaseCapacity(hold);
                store.saveHold(hold.updated(CodHold.Status.CONSUMED, committed, hold.releasedAt(), LocalDateTime.now(clock)));
            } else if (hold.status() == CodHold.Status.COMMITTED) transition(hold.holdId(), CodHold.Status.CONSUMED);
        }
    }
    @Override public void expireDueHolds() {
        store.lockExpired(LocalDateTime.now(clock), EXPIRY_SCAN_LIMIT)
                .forEach(hold -> transition(hold.holdId(), CodHold.Status.EXPIRED));
    }
    private void releaseCapacity(CodHold hold) {
        var account = store.lockShipper(hold.shipperId()).orElseThrow(() -> new IllegalStateException("Shipper balance missing for COD hold"));
        store.saveReservedCapacity(account.withBalance(account.balance().releaseCodCapacity(hold.amount())));
    }
}
