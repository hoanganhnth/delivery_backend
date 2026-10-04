package com.delivery.settlement_service.adapter;

import com.delivery.settlement.application.api.cod.CodCapacityStore;
import com.delivery.settlement.domain.cod.CodHold;
import com.delivery.settlement.domain.ledger.LedgerAccount;
import com.delivery.settlement_service.entity.CodCapacityHold;
import com.delivery.settlement_service.entity.CodCapacityHoldStatus;
import com.delivery.settlement_service.entity.EntityType;
import com.delivery.settlement_service.repository.BalanceRepository;
import com.delivery.settlement_service.repository.CodCapacityHoldRepository;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JpaCodCapacityAdapter implements CodCapacityStore {
    private final BalanceRepository balances;
    private final CodCapacityHoldRepository holds;
    public JpaCodCapacityAdapter(BalanceRepository balances, CodCapacityHoldRepository holds) {
        this.balances = balances;
        this.holds = holds;
    }
    @Override public Optional<LedgerAccount> lockShipper(Long shipperId) {
        return balances.findByEntityIdAndEntityTypeForUpdate(shipperId, EntityType.SHIPPER).map(JpaLedgerAdapter::account);
    }
    @Override public void saveReservedCapacity(LedgerAccount account) {
        var row = balances.findById(account.id()).orElseThrow(() -> new IllegalStateException("Shipper balance missing for COD hold"));
        // Only reservation changes here; preserve the already managed ledger fields.
        row.setReservedDepositBalance(account.balance().reservedDeposit());
        balances.save(row);
    }
    @Override public Optional<CodHold> lockByKey(String key) { return holds.findByIdempotencyKeyForUpdate(key).map(this::snapshot); }
    @Override public Optional<CodHold> lockHold(UUID id) { return holds.findByIdForUpdate(id).map(this::snapshot); }
    @Override public List<CodHold> lockActiveForDelivery(Long id) { return holds.findActiveByDeliveryIdForUpdate(id).stream().map(this::snapshot).toList(); }
    @Override public List<CodHold> lockExpired(LocalDateTime now, int limit) {
        return holds.findExpiredHeldForUpdate(now, PageRequest.of(0, limit)).stream().map(this::snapshot).toList();
    }
    @Override public CodHold saveHold(CodHold value) {
        var row = holds.findById(value.holdId()).orElseGet(CodCapacityHold::new);
        row.setHoldId(value.holdId());
        row.setOfferId(value.offerId());
        row.setOrderId(value.orderId());
        row.setDeliveryId(value.deliveryId());
        row.setShipperId(value.shipperId());
        row.setMatchingSessionId(value.matchingSessionId());
        row.setWaveId(value.waveId());
        row.setAmount(value.amount());
        row.setStatus(JpaLedgerAdapter.enumValue(value.status(), CodCapacityHoldStatus.class));
        row.setExpiresAt(value.expiresAt());
        row.setEventId(value.eventId());
        row.setIdempotencyKey(value.idempotencyKey());
        row.setCreatedAt(value.createdAt());
        row.setCommittedAt(value.committedAt());
        row.setReleasedAt(value.releasedAt());
        row.setConsumedAt(value.consumedAt());
        return snapshot(holds.save(row));
    }
    public CodCapacityHold requireEntity(UUID id) {
        return holds.findById(id).orElseThrow(() -> new IllegalArgumentException("COD hold not found"));
    }
    private CodHold snapshot(CodCapacityHold row) {
        return new CodHold(row.getHoldId(), row.getOfferId(), row.getOrderId(), row.getDeliveryId(), row.getShipperId(),
                row.getMatchingSessionId(), row.getWaveId(), row.getAmount(), JpaLedgerAdapter.enumValue(row.getStatus(), CodHold.Status.class),
                row.getExpiresAt(), row.getEventId(), row.getIdempotencyKey(), row.getCreatedAt(), row.getCommittedAt(), row.getReleasedAt(), row.getConsumedAt());
    }
}
