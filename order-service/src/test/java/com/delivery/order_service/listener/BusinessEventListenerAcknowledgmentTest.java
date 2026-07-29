package com.delivery.order_service.listener;

import com.delivery.order_service.dto.event.PaymentEvent;
import com.delivery.order_service.dto.event.RestaurantEvent;
import com.delivery.order_service.service.OrderEventService;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class BusinessEventListenerAcknowledgmentTest {

    @Test
    void paymentEventAcknowledgesOnlyAfterStateMutationSucceeds() {
        OrderEventService service = mock(OrderEventService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        PaymentEvent event = new PaymentEvent();
        event.setOrderId(10L);

        new PaymentEventListener(service)
                .handlePaymentCompleted(event, "payment.completed", 0, "1", acknowledgment);

        verify(service).handlePaymentCompleted(event);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void paymentFailureDoesNotAcknowledgeKafkaRecord() {
        OrderEventService service = mock(OrderEventService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        PaymentEvent event = new PaymentEvent();
        event.setOrderId(10L);
        doThrow(new IllegalStateException("database unavailable"))
                .when(service).handlePaymentFailed(event);

        assertThrows(IllegalStateException.class, () -> new PaymentEventListener(service)
                .handlePaymentFailed(event, "payment.failed", 0, "2", acknowledgment));

        verifyNoInteractions(acknowledgment);
    }

    @Test
    void restaurantEventAcknowledgesOnlyAfterStateMutationSucceeds() {
        OrderEventService service = mock(OrderEventService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        RestaurantEvent event = new RestaurantEvent();
        event.setEventId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        event.setOrderId(11L);

        new RestaurantEventListener(service)
                .handleRestaurantConfirmed(event, "restaurant.order-confirmed", 0, "3", acknowledgment);

        verify(service).handleRestaurantConfirmed(event);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void restaurantFailureDoesNotAcknowledgeKafkaRecord() {
        OrderEventService service = mock(OrderEventService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        RestaurantEvent event = new RestaurantEvent();
        event.setEventId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        event.setOrderId(11L);
        doThrow(new IllegalStateException("database unavailable"))
                .when(service).handleRestaurantRejected(event);

        assertThrows(IllegalStateException.class, () -> new RestaurantEventListener(service)
                .handleRestaurantRejected(event, "restaurant.order-rejected", 0, "4", acknowledgment));

        verifyNoInteractions(acknowledgment);
    }

    @Test
    void restaurantDecisionWithoutStableEventIdIsNotAcknowledged() {
        OrderEventService service = mock(OrderEventService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        RestaurantEvent event = new RestaurantEvent();
        event.setOrderId(11L);

        assertThrows(IllegalArgumentException.class, () -> new RestaurantEventListener(service)
                .handleRestaurantConfirmed(
                        event, "restaurant.order-confirmed", 0, "5", acknowledgment));

        verifyNoInteractions(service, acknowledgment);
    }
}
