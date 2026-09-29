package com.delivery.web_bff.domain.session;

public class ApiProxyRejectedException extends RuntimeException {
    public ApiProxyRejectedException(String message) {
        super(message);
    }
}
