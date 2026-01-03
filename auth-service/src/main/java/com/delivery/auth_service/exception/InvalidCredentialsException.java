package com.delivery.auth_service.exception;

/**
 * Exception ném khi thông tin đăng nhập không hợp lệ
 */
public class InvalidCredentialsException extends RuntimeException {
    
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
