package com.delivery.promotion_service.listener;

import com.delivery.promotion_service.service.PromotionOrderReservationEventProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderReservationEventListenerTest {
    @Mock PromotionOrderReservationEventProcessor processor;
    @Mock Acknowledgment acknowledgment;

    @Test
    void acknowledgesOnlyAfterTheDurableProcessorCommits() throws Exception {
        OrderReservationEventListener listener = new OrderReservationEventListener(processor);
        String payload = "{\"eventId\":\"a3b8757d-7a85-48b3-a6b8-5973157e954d\",\"orderId\":9}";
        listener.consume(payload, "order.created", acknowledgment);
        verify(processor).process(payload, "order.created");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void processorFailureIsNotAcknowledged() throws Exception {
        OrderReservationEventListener listener = new OrderReservationEventListener(processor);
        String payload = "{\"eventId\":\"a3b8757d-7a85-48b3-a6b8-5973157e954d\",\"orderId\":9}";
        doThrow(new IllegalArgumentException("poison"))
                .when(processor).process(payload, "order.created");

        assertThrows(IllegalArgumentException.class,
                () -> listener.consume(payload, "order.created", acknowledgment));
        verify(processor).process(payload, "order.created");
        verifyNoInteractions(acknowledgment);
    }
}
