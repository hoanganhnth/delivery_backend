package com.delivery.flashsale_service.listener;

import com.delivery.flashsale_service.service.FlashSaleOrderReservationEventProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OrderReservationEventListenerTest {
    @Mock FlashSaleOrderReservationEventProcessor processor;
    @Mock Acknowledgment acknowledgment;

    @Test
    void acknowledgesOnlyAfterTheProcessorCommitsTheReceiptAndTransition() throws Exception {
        OrderReservationEventListener listener = new OrderReservationEventListener(processor);

        listener.consume("payload", "order.created", acknowledgment);

        verify(processor).process("payload", "order.created");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void processingFailureLeavesTheRecordUnacknowledgedForRetryOrDlt() throws Exception {
        OrderReservationEventListener listener = new OrderReservationEventListener(processor);
        org.mockito.Mockito.doThrow(new IllegalArgumentException("poison"))
                .when(processor).process("payload", "order.created");

        assertThrows(IllegalArgumentException.class,
                () -> listener.consume("payload", "order.created", acknowledgment));

        verify(processor).process("payload", "order.created");
        verifyNoInteractions(acknowledgment);
    }
}
