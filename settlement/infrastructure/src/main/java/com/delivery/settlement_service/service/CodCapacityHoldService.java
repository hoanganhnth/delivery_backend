package com.delivery.settlement_service.service;

import com.delivery.settlement.application.api.cod.CodCapacityUseCase;
import com.delivery.settlement.application.cod.DefaultCodCapacityUseCase;
import com.delivery.settlement.domain.cod.CodHold;
import com.delivery.settlement.domain.cod.CodHoldCommand;
import com.delivery.settlement.domain.ledger.InsufficientWalletFunds;
import com.delivery.settlement_service.adapter.JpaCodCapacityAdapter;
import com.delivery.settlement_service.adapter.JpaLedgerAdapter;
import com.delivery.settlement_service.dto.request.CodCapacityHoldRequest;
import com.delivery.settlement_service.entity.CodCapacityHold;
import com.delivery.settlement_service.entity.CodCapacityHoldStatus;
import com.delivery.settlement_service.exception.InsufficientBalanceException;
import com.delivery.settlement_service.repository.BalanceRepository;
import com.delivery.settlement_service.repository.CodCapacityHoldRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** HTTP/listener/scheduler facade; reservation and transition decisions live in the core. */
@Service
public class CodCapacityHoldService {
    private final JpaCodCapacityAdapter adapter;
    private final CodCapacityUseCase core;
    public CodCapacityHoldService(BalanceRepository balances, CodCapacityHoldRepository holds) {
        this.adapter = new JpaCodCapacityAdapter(balances, holds);
        this.core = new DefaultCodCapacityUseCase(adapter, Clock.systemDefaultZone(), UUID::randomUUID);
    }
    @Transactional
    public List<CodCapacityHold> hold(CodCapacityHoldRequest request) {
        try {
            var command = request == null ? null : new CodHoldCommand(request.getEventId(), request.getShipperId(),
                    request.getMatchingSessionId(), request.getWaveId(), request.getOffers() == null ? null : request.getOffers().stream()
                    .map(item -> item == null ? null : new CodHoldCommand.Item(item.getHoldId(), item.getOfferId(), item.getOrderId(),
                            item.getDeliveryId(), item.getAmount(), item.getExpiresAt())).toList());
            return core.hold(command).stream().map(value -> adapter.requireEntity(value.holdId()))
                    .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        } catch (InsufficientWalletFunds failure) { throw new InsufficientBalanceException(failure.getMessage()); }
    }
    @Transactional
    public CodCapacityHold transition(UUID holdId, CodCapacityHoldStatus target) {
        return adapter.requireEntity(core.transition(holdId, JpaLedgerAdapter.enumValue(target, CodHold.Status.class)).holdId());
    }
    @Transactional
    public void consumeForDelivery(Long deliveryId) { core.consumeForDelivery(deliveryId); }
    @Scheduled(fixedDelayString = "${settlement.cod-hold.expiry-scan-ms:1000}")
    @Transactional
    public void expireDueHolds() { core.expireDueHolds(); }
}
