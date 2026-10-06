package com.delivery.notification_service.listener;

import com.delivery.notification_service.service.NotificationService;
import com.delivery.order.contracts.OrderCreatedEvent;
import com.delivery.delivery.contracts.ShipperFoundEvent;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationListenerAcknowledgmentTest {

    private final NotificationService notificationService = mock(NotificationService.class);
    private final Acknowledgment acknowledgment = mock(Acknowledgment.class);
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void orderFailureIsNotAcknowledged() throws Exception {
        doThrow(new RuntimeException("database unavailable"))
                .when(notificationService).sendOrderCreatedNotification(
                        any(), anyLong(), isNull(), anyLong(), anyString());

        OrderCreatedEvent event = objectMapper.readValue(
                "{\"eventId\":\"11111111-1111-1111-1111-111111111111\",\"orderId\":7,\"userId\":42,\"restaurantName\":\"R\"}",
                OrderCreatedEvent.class);

        assertThrows(IllegalStateException.class, () -> new OrderEventListener(notificationService)
                .handleOrderCreatedEvent(event, "order.created", 0, 1L, acknowledgment));

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void deliveryFailureIsNotAcknowledged() {
        doThrow(new RuntimeException("database unavailable"))
                .when(notificationService).sendDeliveryStatusNotification(
                        any(), anyLong(), isNull(), anyLong(), anyString(), any());

        assertThrows(IllegalStateException.class, () -> new DeliveryEventListener(notificationService)
                .handleDeliveryStatusUpdatedEvent(
                        "{\"eventId\":\"22222222-2222-2222-2222-222222222222\",\"deliveryId\":8,\"orderId\":7,\"userId\":42,\"status\":\"ASSIGNED\"}",
                        "delivery.status-updated", 0, 1L, acknowledgment));

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void shipperOfferFailureIsNotAcknowledged() throws Exception {
        doThrow(new RuntimeException("database unavailable"))
                .when(notificationService).sendShipperMatchFoundNotification(
                        anyLong(), anyLong(), anyString(), anyString(), anyString(), anyDouble(), anyString());

        ShipperFoundEvent event = objectMapper.readValue("""
                {"eventId":"11111111-1111-1111-1111-111111111111","deliveryId":8,"orderId":7,"restaurantName":"R","pickupAddress":"P",
                 "deliveryAddress":"D","availableShippers":[{"shipperId":5,"distanceKm":1.2}]}
                """, ShipperFoundEvent.class);

        assertThrows(IllegalStateException.class, () -> new MatchEventListener(notificationService)
                .handleShipperFoundEvent(event, "delivery.shipper-offered", 0, 1L, acknowledgment));

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void shipperOfferWithoutStableEventIdIsNotAcknowledgedOrDispatched() throws Exception {
        ShipperFoundEvent event = objectMapper.readValue("""
                {"deliveryId":8,"orderId":7,"restaurantName":"R","pickupAddress":"P",
                 "deliveryAddress":"D","availableShippers":[{"shipperId":5,"distanceKm":1.2}]}
                """, ShipperFoundEvent.class);

        assertThrows(IllegalArgumentException.class, () -> new MatchEventListener(notificationService)
                .handleShipperFoundEvent(event, "delivery.shipper-offered", 0, 1L, acknowledgment));

        verifyNoInteractions(notificationService);
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void shipperOfferWithoutCanonicalDisplayFactsIsNotAcknowledgedOrDispatched() throws Exception {
        ShipperFoundEvent event = objectMapper.readValue("""
                {"eventId":"11111111-1111-1111-1111-111111111111","deliveryId":8,"orderId":7,
                 "availableShippers":[{"shipperId":5,"distanceKm":1.2}]}
                """, ShipperFoundEvent.class);

        assertThrows(IllegalArgumentException.class, () -> new MatchEventListener(notificationService)
                .handleShipperFoundEvent(event, "delivery.shipper-offered", 0, 1L, acknowledgment));

        verifyNoInteractions(notificationService);
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void shipperOfferWithInvalidAggregateIdentityIsNotAcknowledgedOrDispatched() throws Exception {
        ShipperFoundEvent event = objectMapper.readValue("""
                {"eventId":"11111111-1111-1111-1111-111111111111","deliveryId":0,"orderId":7,
                 "availableShippers":[{"shipperId":5,"distanceKm":1.2}]}
                """, ShipperFoundEvent.class);

        assertThrows(IllegalArgumentException.class, () -> new MatchEventListener(notificationService)
                .handleShipperFoundEvent(event, "delivery.shipper-offered", 0, 1L, acknowledgment));

        verifyNoInteractions(notificationService);
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void orderEventWithoutStableIdentityIsNotAcknowledgedOrDispatched() throws Exception {
        OrderCreatedEvent event = objectMapper.readValue("{\"orderId\":7,\"userId\":42}", OrderCreatedEvent.class);

        assertThrows(IllegalArgumentException.class, () -> new OrderEventListener(notificationService)
                .handleOrderCreatedEvent(event, "order.created", 0, 1L, acknowledgment));

        verifyNoInteractions(notificationService);
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void orderEventWithoutCanonicalRestaurantNameIsNotAcknowledgedOrDispatched() throws Exception {
        OrderCreatedEvent event = objectMapper.readValue("{\"eventId\":\"11111111-1111-1111-1111-111111111111\",\"orderId\":7,\"userId\":42}", OrderCreatedEvent.class);

        assertThrows(IllegalArgumentException.class, () -> new OrderEventListener(notificationService)
                .handleOrderCreatedEvent(event, "order.created", 0, 1L, acknowledgment));

        verifyNoInteractions(notificationService);
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void legacyDeliveryStatusVocabularyIsNotAcknowledgedOrDispatched() {
        assertThrows(IllegalArgumentException.class, () -> new DeliveryEventListener(notificationService)
                .handleDeliveryStatusUpdatedEvent(
                        "{\"eventId\":\"33333333-3333-3333-3333-333333333333\","
                                + "\"deliveryId\":8,\"orderId\":7,\"userId\":42,"
                                + "\"status\":\"IN_PROGRESS\"}",
                        "delivery.status-updated", 0, 1L, acknowledgment));

        verifyNoInteractions(notificationService);
        verify(acknowledgment, never()).acknowledge();
    }
}
