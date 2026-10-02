package com.delivery.auth_service.exception;

/**
 * Exception ném khi email đã tồn tại trong hệ thống
 */
public class EmailAlreadyExistsException extends RuntimeException {
    
    public EmailAlreadyExistsException(String message) {
        super(message);
    }
}
