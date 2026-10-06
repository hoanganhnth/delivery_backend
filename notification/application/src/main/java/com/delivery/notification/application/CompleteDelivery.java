package com.delivery.notification.application;

import com.delivery.notification.application.api.DeliveryPort;
import com.delivery.notification.domain.NotificationLifecycle;
import com.delivery.notification.domain.PushEligibility;
import java.time.LocalDateTime;
import java.util.function.Supplier;

public final class CompleteDelivery {
    private final DeliveryPort port;
    private final Supplier<LocalDateTime> now;

    public CompleteDelivery(DeliveryPort port, Supplier<LocalDateTime> now) {
        this.port = port;
        this.now = now;
    }

    public void deliver(Long id, Boolean sendPush) {
        var locked = port.lock(id).orElseThrow(() -> new IllegalStateException("Notification not found: " + id));
        if (!NotificationLifecycle.shouldDeliver(locked.id(), locked.status())) return;
        var payload = locked.payload();
        if (PushEligibility.requested(sendPush)) port.push(payload.userId(), payload.title(), payload.message(),
                NotificationLifecycle.pushData(locked.id(), payload));
        port.saveSent(locked.id(), now.get());
    }
}
