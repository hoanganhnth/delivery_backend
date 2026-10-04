package com.delivery.tracking.domain;

public final class TrackingAccessDeniedException extends RuntimeException {
    public TrackingAccessDeniedException(String message) {
        super(message);
    }
}
