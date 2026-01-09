package com.delivery.livestream_service.exception;

public class LivestreamNotFoundException extends RuntimeException {
    
    public LivestreamNotFoundException(String message) {
        super(message);
    }
}
