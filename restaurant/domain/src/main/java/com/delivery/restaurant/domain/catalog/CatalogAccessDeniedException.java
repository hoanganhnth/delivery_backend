package com.delivery.restaurant.domain.catalog;

public final class CatalogAccessDeniedException extends RuntimeException {
    public CatalogAccessDeniedException(String message) { super(message); }
}
