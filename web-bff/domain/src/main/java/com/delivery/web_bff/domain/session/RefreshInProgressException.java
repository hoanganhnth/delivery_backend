package com.delivery.web_bff.domain.session;

public class RefreshInProgressException extends RuntimeException {
    public RefreshInProgressException(String message) {
        super(message);
    }
}
