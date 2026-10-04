package com.delivery.tracking.domain;

/** Established source values and their publication/failure ordering policy. */
public enum LocationUpdateSource {
    APPLICATION, WEBSOCKET;

    public boolean publishesBeforeFanout() {
        return this == WEBSOCKET;
    }
}
