package com.delivery.analytics_service.listener;

import com.delivery.analytics_service.service.EventProcessingService;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AnalyticsListenerFailureHandlingTest {

    @Test
    void malformedOrderEventIsNotAcknowledged() {
        EventProcessingService service = mock(EventProcessingService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        OrderEventListener listener = new OrderEventListener(service);

        assertThrows(IllegalStateException.class,
                () -> listener.onOrderCreated("not-json", acknowledgment));

        verifyNoInteractions(service, acknowledgment);
    }

    @Test
    void malformedPaymentEventIsNotAcknowledged() {
        EventProcessingService service = mock(EventProcessingService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        PaymentEventListener listener = new PaymentEventListener(service);

        assertThrows(IllegalStateException.class,
                () -> listener.onPaymentCompleted("not-json", acknowledgment));

        verifyNoInteractions(service, acknowledgment);
    }
}
