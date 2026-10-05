package com.delivery.notification.application.api;

import com.delivery.notification.domain.NotificationIntent;

/** Adapter preserves raw JSON serialization and the existing durable-send boundary. */
@FunctionalInterface
public interface EventNotificationPort {
    void send(NotificationIntent intent);
}
