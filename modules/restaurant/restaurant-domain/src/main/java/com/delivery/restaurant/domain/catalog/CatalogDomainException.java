package com.delivery.restaurant.domain.catalog;

public final class CatalogDomainException extends IllegalArgumentException {

    private final CatalogRuleViolation violation;

    public CatalogDomainException(CatalogRuleViolation violation, String message) {
        super(message);
        this.violation = violation;
    }

    public CatalogRuleViolation violation() {
        return violation;
    }
}
