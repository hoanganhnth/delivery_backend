package com.delivery.notification_service.listener;

import com.delivery.notification_service.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationListenerAcknowledgmentTest {

    private final NotificationService notificationService = mock(NotificationService.class);
    private final Acknowledgment acknowledgment = mock(Acknowledgment.class);

    @Test
    void orderFailureIsNotAcknowledged() {
        doThrow(new RuntimeException("database unavailable"))
                .when(notificationService).sendOrderCreatedNotification(anyLong(), anyLong(), anyString());

        assertThrows(IllegalStateException.class, () -> new OrderEventListener(notificationService)
                .handleOrderCreatedEvent(
                        "{\"eventId\":\"11111111-1111-1111-1111-111111111111\",\"orderId\":7,\"userId\":42,\"restaurantName\":\"R\"}",
                        "order.created", 0, 1L, acknowledgment));

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void deliveryFailureIsNotAcknowledged() {
        doThrow(new RuntimeException("database unavailable"))
                .when(notificationService).sendDeliveryStatusNotification(anyLong(), anyLong(), anyString(), any());

        assertThrows(IllegalStateException.class, () -> new DeliveryEventListener(notificationService)
                .handleDeliveryStatusUpdatedEvent(
                        "{\"eventId\":\"22222222-2222-2222-2222-222222222222\",\"deliveryId\":8,\"orderId\":7,\"userId\":42,\"status\":\"ASSIGNED\"}",
                        "delivery.status-updated", 0, 1L, acknowledgment));

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void shipperOfferFailureIsNotAcknowledged() {
        doThrow(new RuntimeException("database unavailable"))
                .when(notificationService).sendShipperMatchFoundNotification(
                        anyLong(), anyLong(), anyString(), anyString(), anyString(), anyDouble(), anyString());

        String event = """
                {"eventId":"offer-event-1","deliveryId":8,"orderId":7,"restaurantName":"R","pickupAddress":"P",
                 "deliveryAddress":"D","availableShippers":[{"shipperId":5,"distanceKm":1.2}]}
                """;

        assertThrows(IllegalStateException.class, () -> new MatchEventListener(notificationService)
                .handleShipperFoundEvent(event, "delivery.shipper-offered", 0, 1L, acknowledgment));

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void shipperOfferWithoutStableEventIdIsNotAcknowledgedOrDispatched() {
        String event = """
                {"deliveryId":8,"orderId":7,"restaurantName":"R","pickupAddress":"P",
                 "deliveryAddress":"D","availableShippers":[{"shipperId":5,"distanceKm":1.2}]}
                """;

        assertThrows(IllegalStateException.class, () -> new MatchEventListener(notificationService)
                .handleShipperFoundEvent(event, "delivery.shipper-offered", 0, 1L, acknowledgment));

        verifyNoInteractions(notificationService);
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void shipperOfferWithoutCanonicalDisplayFactsIsNotAcknowledgedOrDispatched() {
        String event = """
                {"eventId":"offer-event-1","deliveryId":8,"orderId":7,
                 "availableShippers":[{"shipperId":5,"distanceKm":1.2}]}
                """;

        assertThrows(IllegalStateException.class, () -> new MatchEventListener(notificationService)
                .handleShipperFoundEvent(event, "delivery.shipper-offered", 0, 1L, acknowledgment));

        verifyNoInteractions(notificationService);
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void shipperOfferWithInvalidAggregateIdentityIsNotAcknowledgedOrDispatched() {
        String event = """
                {"eventId":"offer-event-1","deliveryId":0,"orderId":7,
                 "availableShippers":[{"shipperId":5,"distanceKm":1.2}]}
                """;

        assertThrows(IllegalStateException.class, () -> new MatchEventListener(notificationService)
                .handleShipperFoundEvent(event, "delivery.shipper-offered", 0, 1L, acknowledgment));

        verifyNoInteractions(notificationService);
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void orderEventWithoutStableIdentityIsNotAcknowledgedOrDispatched() {
        assertThrows(IllegalStateException.class, () -> new OrderEventListener(notificationService)
                .handleOrderCreatedEvent(
                        "{\"orderId\":7,\"userId\":42}",
                        "order.created", 0, 1L, acknowledgment));

        verifyNoInteractions(notificationService);
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void orderEventWithoutCanonicalRestaurantNameIsNotAcknowledgedOrDispatched() {
        assertThrows(IllegalStateException.class, () -> new OrderEventListener(notificationService)
                .handleOrderCreatedEvent(
                        "{\"eventId\":\"11111111-1111-1111-1111-111111111111\",\"orderId\":7,\"userId\":42}",
                        "order.created", 0, 1L, acknowledgment));

        verifyNoInteractions(notificationService);
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void legacyDeliveryStatusVocabularyIsNotAcknowledgedOrDispatched() {
        assertThrows(IllegalStateException.class, () -> new DeliveryEventListener(notificationService)
                .handleDeliveryStatusUpdatedEvent(
                        "{\"eventId\":\"33333333-3333-3333-3333-333333333333\","
                                + "\"deliveryId\":8,\"orderId\":7,\"userId\":42,"
                                + "\"status\":\"IN_PROGRESS\"}",
                        "delivery.status-updated", 0, 1L, acknowledgment));

        verifyNoInteractions(notificationService);
        verify(acknowledgment, never()).acknowledge();
    }
}
