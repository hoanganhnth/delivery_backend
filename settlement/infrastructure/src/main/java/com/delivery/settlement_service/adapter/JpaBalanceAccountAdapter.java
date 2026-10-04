package com.delivery.settlement_service.adapter;

import com.delivery.settlement.application.api.ledger.BalanceAccountStore;
import com.delivery.settlement.application.ledger.BalanceInsertConflict;
import com.delivery.settlement.domain.ledger.*;
import com.delivery.settlement_service.entity.Balance;
import com.delivery.settlement_service.entity.EntityType;
import com.delivery.settlement_service.repository.BalanceRepository;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.Optional;
import static com.delivery.settlement_service.adapter.JpaLedgerAdapter.*;

public final class JpaBalanceAccountAdapter implements BalanceAccountStore {
    private final BalanceRepository balances;
    public JpaBalanceAccountAdapter(BalanceRepository balances) { this.balances = balances; }
    @Override public Optional<LedgerAccount> findAccount(LedgerOwner owner) {
        return balances.findByEntityIdAndEntityType(owner.entityId(), enumValue(owner.entityType(), EntityType.class))
                .map(JpaLedgerAdapter::account);
    }
    @Override public LedgerAccount insertAccount(LedgerOwner owner, WalletBalance initialBalance) {
        var row = new Balance();
        row.setEntityId(owner.entityId());
        row.setEntityType(enumValue(owner.entityType(), EntityType.class));
        applyBalance(row, initialBalance);
        try { return account(balances.saveAndFlush(row)); }
        catch (DataIntegrityViolationException conflict) { throw new BalanceInsertConflict(conflict); }
    }
}
