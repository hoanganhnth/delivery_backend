package com.delivery.notification_service.listener;

import com.delivery.notification_service.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.eq;

class DeliveryEventListenerContractTest {

    @Test
    void canonicalDeliveryStatusPayloadNotifiesCustomerAndAcknowledges() {
        NotificationService notificationService = mock(NotificationService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        DeliveryEventListener listener = new DeliveryEventListener(notificationService);

        listener.handleDeliveryStatusUpdatedEvent("""
                        {"eventId":"7ec910c4-7928-4d80-9f32-fdd83058f31b",
                         "deliveryId":11,"orderId":22,"userId":33,"shipperId":44,
                         "status":"DELIVERING","newStatus":"DELIVERING",
                         "oldStatus":"PICKED_UP","eventType":"DELIVERY_STATUS_UPDATED"}
                        """,
                "delivery.status-updated", 0, System.currentTimeMillis(), acknowledgment);

        verify(notificationService).sendDeliveryStatusNotification(
                eq(33L), eq(11L), eq("DELIVERING"), isNull());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void shipperNotFoundStatusWithoutAssignedShipperNotifiesCustomerAndAcknowledges() {
        NotificationService notificationService = mock(NotificationService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        DeliveryEventListener listener = new DeliveryEventListener(notificationService);

        listener.handleDeliveryStatusUpdatedEvent("""
                        {"eventId":"8ec910c4-7928-4d80-9f32-fdd83058f31b",
                         "deliveryId":11,"orderId":22,"userId":33,
                         "status":"SHIPPER_NOT_FOUND","newStatus":"SHIPPER_NOT_FOUND",
                         "oldStatus":"FINDING_SHIPPER","eventType":"DELIVERY_STATUS_UPDATED"}
                        """,
                "delivery.status-updated", 0, System.currentTimeMillis(), acknowledgment);

        verify(notificationService).sendDeliveryStatusNotification(
                eq(33L), eq(11L), eq("SHIPPER_NOT_FOUND"), isNull());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void canonicalDeliveryStatusPayloadKeepsProducerOwnedShipperNameWhenPresent() {
        NotificationService notificationService = mock(NotificationService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        DeliveryEventListener listener = new DeliveryEventListener(notificationService);

        listener.handleDeliveryStatusUpdatedEvent("""
                        {"eventId":"9ec910c4-7928-4d80-9f32-fdd83058f31b",
                         "deliveryId":11,"orderId":22,"userId":33,"shipperId":44,
                         "shipperName":"Nguyen Van A",
                         "status":"DELIVERING","newStatus":"DELIVERING",
                         "oldStatus":"PICKED_UP","eventType":"DELIVERY_STATUS_UPDATED"}
                        """,
                "delivery.status-updated", 0, System.currentTimeMillis(), acknowledgment);

        verify(notificationService).sendDeliveryStatusNotification(
                33L, 11L, "DELIVERING", "Nguyen Van A");
        verify(acknowledgment).acknowledge();
    }
}
