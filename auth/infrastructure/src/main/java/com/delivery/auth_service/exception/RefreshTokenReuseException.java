package com.delivery.auth_service.exception;

public class RefreshTokenReuseException extends InvalidTokenException {
    public RefreshTokenReuseException(String message) {
        super(message);
    }
}
