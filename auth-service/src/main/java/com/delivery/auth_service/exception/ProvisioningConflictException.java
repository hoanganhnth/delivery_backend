package com.delivery.auth_service.exception;

public class ProvisioningConflictException extends RuntimeException {
    public ProvisioningConflictException(String message) {
        super(message);
    }
}
