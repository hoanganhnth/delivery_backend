package com.delivery.notification.application;

import com.delivery.notification.application.api.*;
import com.delivery.notification.domain.NotificationLifecycle;

/** No enclosing transaction: durable claim must finish before external delivery. */
public final class DurableSend<R> {
    private final DurableSendPort<R> port;

    public DurableSend(DurableSendPort<R> port) { this.port = port; }

    public R send(SendCommand command) {
        NotificationLifecycle.validate(command == null ? null : command.payload());
        boolean keyed = NotificationLifecycle.hasKey(command.deduplicationKey());
        StoredNotification<R> existing = keyed ? port.findByKey(command.deduplicationKey()).orElse(null) : null;
        if (existing != null) {
            assertReplay(existing, command);
            if (NotificationLifecycle.retryPending(existing.status())) deliver(command, existing);
            return existing.response();
        }
        StoredNotification<R> created = port.createCommitted(command);
        if (keyed) assertReplay(created, command);
        // Even an already-SENT concurrent claim goes through the locked coordinator,
        // just as the original insert/reread path did.
        deliver(command, created);
        return created.response();
    }

    private void assertReplay(StoredNotification<R> stored, SendCommand command) {
        if (!stored.payload().matches(command.payload())) throw new ReplayConflictException();
    }

    private void deliver(SendCommand command, StoredNotification<R> stored) {
        port.deliver(command, stored);
        port.markResponseSent(stored.response());
    }
}
