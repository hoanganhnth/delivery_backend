package com.delivery.settlement_service.payment.contract;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Immutable monetary value used at the provider boundary. The Settlement
 * ledger stores two decimal places, so requests are canonicalized without
 * silently rounding. Provider adapters must not reconstruct amounts in a
 * client or from a mutable catalog.
 */
public record MoneyAmount(BigDecimal amount, String currency) {

    public MoneyAmount {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("amount supports at most two decimal places");
        }
        if (currency == null || !currency.trim().matches("[A-Za-z]{3}")) {
            throw new IllegalArgumentException("currency must be an ISO-4217 three-letter code");
        }
        amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        currency = currency.trim().toUpperCase(Locale.ROOT);
    }

    public static MoneyAmount vnd(BigDecimal amount) {
        return new MoneyAmount(amount, "VND");
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }
}
