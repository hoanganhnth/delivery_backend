package com.delivery.restaurant.domain.ownership;

public final class ManagementAccessException extends RuntimeException {
    private final ManagementAccessFailure failure;

    public ManagementAccessException(ManagementAccessFailure failure) {
        super(failure.name());
        this.failure = failure;
    }

    public ManagementAccessFailure failure() {
        return failure;
    }
}
