package com.delivery.shipper.domain.profile;

public record OnlineTransition(OnlineStatus previous, OnlineStatus current, boolean changed) {
    public static OnlineTransition decide(OnlineStatus previous, OnlineStatus requested) {
        if (previous == null || requested == null) throw new IllegalArgumentException("online status is required");
        return new OnlineTransition(previous, requested, previous != requested);
    }
}
