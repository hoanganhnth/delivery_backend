package com.delivery.tracking.domain;

/** A subscription accepts only a newer observed fact; equal-time replay is a no-op. */
public final class LocationDeliveryOrder {
    private long latest;
    public synchronized boolean admit(long occurredAt) {
        if (occurredAt <= 0) throw new IllegalArgumentException("positive location occurrence time is required");
        if (occurredAt <= latest) return false;
        latest = occurredAt;
        return true;
    }
}
