package com.delivery.restaurant.infrastructure.serviceability;

public class ServiceabilityZoneConflictException extends RuntimeException {

    public ServiceabilityZoneConflictException(String message) {
        super(message);
    }
}
