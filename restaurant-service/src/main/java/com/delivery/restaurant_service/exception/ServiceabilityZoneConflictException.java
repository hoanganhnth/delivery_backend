package com.delivery.restaurant_service.exception;

public class ServiceabilityZoneConflictException extends RuntimeException {
    public ServiceabilityZoneConflictException(String message) {
        super(message);
    }
}
