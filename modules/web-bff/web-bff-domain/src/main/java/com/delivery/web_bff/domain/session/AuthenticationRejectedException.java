package com.delivery.web_bff.domain.session;

public class AuthenticationRejectedException extends RuntimeException {
    public AuthenticationRejectedException(String message) {
        super(message);
    }
}
