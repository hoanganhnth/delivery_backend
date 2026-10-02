package com.delivery.auth_service.exception;

/**
 * Exception ném khi tài nguyên không tìm thấy
 * (User, Account, Session, etc.)
 */
public class ResourceNotFoundException extends RuntimeException {
    
    public ResourceNotFoundException(String message) {
        super(message);
    }
    
    public ResourceNotFoundException(String resource, String field, Object value) {
        super(String.format("%s not found with %s: %s", resource, field, value));
    }
}
