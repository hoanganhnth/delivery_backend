package com.delivery.restaurant.domain.ownership;

public final class OwnerAssignmentException extends RuntimeException {

    private final OwnerAssignmentFailure failure;

    public OwnerAssignmentException(OwnerAssignmentFailure failure) {
        super(failure.name());
        this.failure = failure;
    }

    public OwnerAssignmentFailure failure() {
        return failure;
    }
}
