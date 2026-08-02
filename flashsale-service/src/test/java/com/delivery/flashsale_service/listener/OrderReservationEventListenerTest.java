package com.delivery.flashsale_service.listener;

import com.delivery.flashsale_service.service.FlashSaleStockService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderReservationEventListenerTest {
    @Mock FlashSaleStockService service;
    @Mock Acknowledgment acknowledgment;

    @Test
    void createdAndCancelledReplayUseStableReservationIdentity() throws Exception {
        OrderReservationEventListener listener = new OrderReservationEventListener(service, new ObjectMapper());
        ReflectionTestUtils.setField(listener, "orderCreatedTopic", "order.created");
        UUID reservationId = UUID.randomUUID();
        String payload = "{\"eventId\":\"%s\",\"orderId\":9,\"flashSaleReservationId\":\"%s\"}"
                .formatted(UUID.randomUUID(), reservationId);
        listener.consume(payload, "order.created", acknowledgment);
        listener.consume(payload, "order.created", acknowledgment);
        listener.consume(payload, "order.cancelled", acknowledgment);
        listener.consume(payload, "order.refund-eligible", acknowledgment);
        verify(service, times(2)).commit(reservationId, 9L);
        verify(service, times(2)).release(reservationId, 9L);
        verify(acknowledgment, times(4)).acknowledge();
    }

    @Test
    void malformedEventIsNotAcknowledged() {
        OrderReservationEventListener listener = new OrderReservationEventListener(service, new ObjectMapper());
        assertThrows(IllegalArgumentException.class,
                () -> listener.consume("{\"orderId\":9}", "order.created", acknowledgment));
        verifyNoInteractions(service, acknowledgment);
    }
}
