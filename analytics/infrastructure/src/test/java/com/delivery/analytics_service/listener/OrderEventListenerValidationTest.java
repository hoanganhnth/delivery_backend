package com.delivery.analytics_service.listener;

import com.delivery.analytics_service.service.EventProcessingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OrderEventListenerValidationTest {
    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "{", "{}", "{\"orderId\":0}",
            "{\"orderId\":-1}", "{\"orderId\":1.5}", "{\"orderId\":9223372036854775808}",
            "{\"orderId\":\"1\"}", "{\"orderId\":1,\"restaurantId\":1.5}"})
    void malformedCancellationCannotReachProjectionOrAcknowledgment(String payload) {
        var service = mock(EventProcessingService.class);
        var acknowledgment = mock(Acknowledgment.class);

        assertThatThrownBy(() -> new OrderEventListener(service).onOrderCancelled(payload, acknowledgment))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(service, acknowledgment);
    }

    @Test
    void createdEventPreservesCanonicalValuesAndAcknowledgesOnlyAfterProjection() {
        var service = mock(EventProcessingService.class);
        var acknowledgment = mock(Acknowledgment.class);
        String payload = "{\"orderId\":1,\"userId\":2,\"restaurantId\":3,"
                + "\"restaurantName\":\"Shop\",\"totalPrice\":12.50,\"paymentMethod\":\"COD\"}";

        new OrderEventListener(service).onOrderCreated(payload, acknowledgment);

        var order = inOrder(service, acknowledgment);
        var amount = org.mockito.ArgumentCaptor.forClass(BigDecimal.class);
        order.verify(service).processOrderCreated(eq(1L), eq(2L), eq(3L), eq("Shop"), amount.capture(), eq("COD"), eq(payload));
        org.assertj.core.api.Assertions.assertThat(amount.getValue()).isEqualByComparingTo("12.50");
        order.verify(acknowledgment).acknowledge();
    }

    @Test
    void cancelledEventRetainsOptionalRestaurantCompatibility() {
        var service = mock(EventProcessingService.class);
        var acknowledgment = mock(Acknowledgment.class);
        String payload = "{\"orderId\":1,\"restaurantId\":null}";

        new OrderEventListener(service).onOrderCancelled(payload, acknowledgment);

        verify(service).processOrderCancelled(1L, null, payload);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void deliveredEventSupportsLegacyNewStatusAndMissingAmount() {
        var service = mock(EventProcessingService.class);
        var acknowledgment = mock(Acknowledgment.class);
        String payload = "{\"orderId\":1,\"newStatus\":\"delivered\"}";

        new OrderEventListener(service).onOrderStatusUpdated(payload, acknowledgment);

        verify(service).processOrderDelivered(1L, null, null, BigDecimal.ZERO, payload);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void projectionFailureIsRetryableAndNeverAcknowledged() {
        var service = mock(EventProcessingService.class);
        var acknowledgment = mock(Acknowledgment.class);
        String payload = "{\"orderId\":1}";
        doThrow(new IllegalStateException("storage unavailable"))
                .when(service).processOrderCancelled(1L, null, payload);

        assertThatThrownBy(() -> new OrderEventListener(service).onOrderCancelled(payload, acknowledgment))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(acknowledgment);
    }
}
