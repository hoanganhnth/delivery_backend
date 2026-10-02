package com.delivery.auth_service.exception;

/**
 * Exception ném khi không có quyền truy cập
 */
public class AccessDeniedException extends RuntimeException {
    
    public AccessDeniedException(String message) {
        super(message);
    }
}
