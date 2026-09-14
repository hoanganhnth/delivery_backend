package com.delivery.livestream_service.exception;

public class LivestreamProductNotFoundException extends RuntimeException {
    public LivestreamProductNotFoundException(String message) {
        super(message);
    }
}
