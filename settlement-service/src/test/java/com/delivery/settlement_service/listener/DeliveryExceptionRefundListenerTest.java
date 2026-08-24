package com.delivery.settlement_service.listener;

import com.delivery.settlement_service.dto.event.DeliveryExceptionReportedEvent;
import com.delivery.settlement_service.service.RefundCaseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryExceptionRefundListenerTest {
    @Mock RefundCaseService refundCaseService;
    @Mock Acknowledgment acknowledgment;

    @Test
    void initialRetryAvailableFactCreatesManualReviewCaseAndAcknowledges() throws Exception {
        DeliveryExceptionRefundListener listener = new DeliveryExceptionRefundListener(refundCaseService);
        DeliveryExceptionReportedEvent event = event("DELIVERY_EXCEPTION_REPORTED", "RETRY_AVAILABLE");

        listener.handleDeliveryException(new ObjectMapper().findAndRegisterModules().writeValueAsString(event),
                "delivery.exception.reported", 0, 1L, acknowledgment);

        verify(refundCaseService).processDeliveryException(any(DeliveryExceptionReportedEvent.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void laterExceptionStateUpdatesAreAcknowledgedWithoutFinancialCaseCreation() throws Exception {
        DeliveryExceptionRefundListener listener = new DeliveryExceptionRefundListener(refundCaseService);
        DeliveryExceptionReportedEvent event = event("DELIVERY_EXCEPTION_UPDATED", "RETURNING");

        listener.handleDeliveryException(new ObjectMapper().findAndRegisterModules().writeValueAsString(event),
                "delivery.exception.reported", 0, 1L, acknowledgment);

        verifyNoInteractions(refundCaseService);
        verify(acknowledgment).acknowledge();
    }

    private DeliveryExceptionReportedEvent event(String type, String status) {
        return DeliveryExceptionReportedEvent.builder()
                .eventId(UUID.randomUUID()).eventType(type).exceptionId(UUID.randomUUID())
                .deliveryId(7L).orderId(70L).exceptionStatus(status).build();
    }
}
