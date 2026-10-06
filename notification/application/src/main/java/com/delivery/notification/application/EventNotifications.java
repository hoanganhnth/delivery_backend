package com.delivery.notification.application;

import com.delivery.notification.application.api.EventNotificationPort;
import com.delivery.notification.domain.NotificationMapping;
import java.util.UUID;

/** Direct event use cases retain their original validation contract. */
public final class EventNotifications {
    private final EventNotificationPort port;
    public EventNotifications(EventNotificationPort port) { this.port = port; }

    public void orderCreated(UUID eventId, Long userId, Long principalId, Long orderId, String restaurant) {
        port.send(NotificationMapping.orderCreated(eventId, userId, principalId, orderId, restaurant));
    }

    public void deliveryStatus(UUID eventId, Long userId, Long principalId, Long deliveryId,
            String status, String shipperName) {
        port.send(NotificationMapping.deliveryStatus(eventId, userId, principalId, deliveryId, status, shipperName));
    }

    public void shipperOffer(Long shipperId, Long orderId, String restaurant, String pickup,
            String delivery, Double distance, String eventId) {
        port.send(NotificationMapping.shipperOffer(shipperId, orderId, restaurant, pickup, delivery, distance, eventId));
    }
}
