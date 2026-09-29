package com.delivery.web_bff.domain.session;

public class SessionRejectedException extends RuntimeException {
    public SessionRejectedException(String message) {
        super(message);
    }
}
