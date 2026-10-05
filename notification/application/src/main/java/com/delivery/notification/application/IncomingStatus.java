package com.delivery.notification.application;

import com.delivery.notification.application.api.StatusEventPort;
import com.delivery.notification.domain.EventIdentity;
import com.delivery.notification.domain.EventIdentity.Status;

public final class IncomingStatus {
    private final StatusEventPort port;
    public IncomingStatus(StatusEventPort port) { this.port = port; }

    public void handle(Status event) {
        EventIdentity.validateStatus(event.eventId(), event.deliveryId(), event.orderId(), event.userId(), event.status());
        port.validateSimulationContext();
        // Status notifications have never suppressed valid simulation records.
        port.send(new Status(event.eventId(), event.deliveryId(), event.orderId(), event.userId(), event.principalId(),
                event.status(), EventIdentity.shipperName(event.shipperName())));
    }
}
