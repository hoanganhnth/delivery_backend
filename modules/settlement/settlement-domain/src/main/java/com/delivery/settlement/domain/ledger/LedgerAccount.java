package com.delivery.settlement.domain.ledger;

public record LedgerAccount(Long id, LedgerOwner owner, WalletBalance balance) {
    public LedgerAccount withBalance(WalletBalance next) { return new LedgerAccount(id, owner, next); }
}
