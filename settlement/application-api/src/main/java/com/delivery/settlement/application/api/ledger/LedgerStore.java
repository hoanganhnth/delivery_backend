package com.delivery.settlement.application.api.ledger;

import com.delivery.settlement.domain.ledger.*;
import java.util.Optional;

/** Mutations join the caller's transaction; account acquisition retains pessimistic row locks. */
public interface LedgerStore {
    LedgerAccount lockOrCreateAccount(LedgerOwner owner);
    Optional<LedgerAccount> lockAccount(LedgerOwner owner);
    Optional<LedgerAccount> readAccount(LedgerOwner owner);
    void saveAccount(LedgerAccount account);
    Optional<LedgerEntry> findEntry(Long id);
    LedgerEntry saveEntry(LedgerEntry entry);
}
