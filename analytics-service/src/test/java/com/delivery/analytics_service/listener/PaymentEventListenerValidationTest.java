package com.delivery.analytics_service.listener;

import com.delivery.analytics_service.service.EventProcessingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PaymentEventListenerValidationTest {
    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "{", "{}", "{\"orderId\":0}",
            "{\"orderId\":1.5}", "{\"orderId\":1,\"userId\":1.5}",
            "{\"orderId\":1,\"amount\":{}}", "{\"orderId\":1,\"amount\":\"invalid\"}",
            "{\"orderId\":1,\"amount\":1e999}"})
    void invalidCompletedPaymentIsRejectedWithoutProjectionOrAck(String payload) {
        var service = mock(EventProcessingService.class);
        var ack = mock(Acknowledgment.class);

        assertThatThrownBy(() -> new PaymentEventListener(service).onPaymentCompleted(payload, ack))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(service, ack);
    }

    @Test
    void completedPaymentKeepsNumericAmountAndOptionalUser() {
        var service = mock(EventProcessingService.class);
        var ack = mock(Acknowledgment.class);
        String payload = "{\"orderId\":1,\"amount\":12.5,\"paymentMethod\":\"COD\"}";

        new PaymentEventListener(service).onPaymentCompleted(payload, ack);

        var sequence = inOrder(service, ack);
        sequence.verify(service).processPaymentCompleted(1L, null, 12.5, "COD", payload);
        sequence.verify(ack).acknowledge();
    }

    @Test
    void missingAmountRemainsZeroForCompatibility() {
        var service = mock(EventProcessingService.class);
        var ack = mock(Acknowledgment.class);
        String payload = "{\"orderId\":1,\"userId\":2,\"amount\":null}";

        new PaymentEventListener(service).onPaymentCompleted(payload, ack);

        verify(service).processPaymentCompleted(1L, 2L, 0.0, null, payload);
        verify(ack).acknowledge();
    }
}
