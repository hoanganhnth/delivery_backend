package com.delivery.notification.application;

/** Translated into the existing host HTTP/Kafka conflict type at the adapter boundary. */
public final class ReplayConflictException extends RuntimeException {
    public ReplayConflictException() {
        super("Deduplication key is already bound to a different notification payload");
    }
}
