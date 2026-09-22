package com.delivery.restaurant_service.exception;

public class StaleVersionException extends RuntimeException {
    public StaleVersionException() {
        super("STALE_VERSION");
    }

    public StaleVersionException(Throwable cause) {
        super("STALE_VERSION", cause);
    }
}
