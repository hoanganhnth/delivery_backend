package com.delivery.settlement.domain.ledger;

public final class InsufficientWalletFunds extends RuntimeException {
    public InsufficientWalletFunds(String message) { super(message); }
}
