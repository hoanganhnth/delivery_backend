package com.delivery.settlement.application.ledger;

/** Adapter reports the existing persistence-integrity conflict path without exposing Spring. */
public final class BalanceInsertConflict extends RuntimeException {
    public BalanceInsertConflict(Throwable cause) { super(cause); }
}
