package com.delivery.livestream_service.exception;

public class UnauthorizedLivestreamAccessException extends RuntimeException {
    
    public UnauthorizedLivestreamAccessException(String message) {
        super(message);
    }
}
