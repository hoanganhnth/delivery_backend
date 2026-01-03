package com.delivery.auth_service.exception;

/**
 * Exception ném khi token không hợp lệ hoặc hết hạn
 */
public class InvalidTokenException extends RuntimeException {
    
    public InvalidTokenException(String message) {
        super(message);
    }
}
