package com.delivery.settlement_service.listener;

import com.delivery.settlement_service.dto.event.OrderCancelledEvent;
import com.delivery.settlement_service.service.RefundCaseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderCancelledRefundListenerTest {
    @Mock RefundCaseService refundCaseService;
    @Mock Acknowledgment acknowledgment;

    @Test
    void validCancellationIsPassedToRefundServiceAndAcknowledged() throws Exception {
        OrderCancelledRefundListener listener = new OrderCancelledRefundListener(refundCaseService);
        OrderCancelledEvent event = event();

        listener.handleOrderCancelled(
                new ObjectMapper().findAndRegisterModules().writeValueAsString(event),
                "order.cancelled", 0, 1L, acknowledgment);

        verify(refundCaseService).processOrderCancellation(any(OrderCancelledEvent.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void cancellationSnapshotKeepsLegacyItemsAndOrderTimestampsCompatible() throws Exception {
        OrderCancelledRefundListener listener = new OrderCancelledRefundListener(refundCaseService);
        OrderCancelledEvent event = event();
        event.setItems(List.of(Map.of("flashSaleItemId", 77L, "quantity", 2)));
        event.setCreatedAt(LocalDateTime.of(2026, 8, 2, 9, 45));
        event.setUpdatedAt(LocalDateTime.of(2026, 8, 2, 10, 0));

        listener.handleOrderCancelled(
                new ObjectMapper().findAndRegisterModules().writeValueAsString(event),
                "order.cancelled", 0, 1L, acknowledgment);

        var captured = org.mockito.ArgumentCaptor.forClass(OrderCancelledEvent.class);
        verify(refundCaseService, times(1)).processOrderCancellation(captured.capture());
        assertThat(captured.getValue().getItems()).containsExactly(Map.of("flashSaleItemId", 77, "quantity", 2));
        assertThat(captured.getValue().getCreatedAt()).isEqualTo(event.getCreatedAt());
        assertThat(captured.getValue().getUpdatedAt()).isEqualTo(event.getUpdatedAt());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void noShipperRefundEligibilityTopicUsesTheSameSnapshotBoundary() throws Exception {
        OrderCancelledEvent event = event();
        event.setEventType("REFUND_ELIGIBLE");
        event.setCurrentStatus("SHIPPER_NOT_FOUND");
        event.setCancelledBy(null);
        event.setCancelledBySource("SYSTEM");
        event.setCancelReasonCode("SHIPPER_NOT_FOUND");

        listener().handleOrderCancelled(
                new ObjectMapper().findAndRegisterModules().writeValueAsString(event),
                "order.refund-eligible", 0, 1L, acknowledgment);

        verify(refundCaseService).processOrderCancellation(any(OrderCancelledEvent.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void malformedJsonIsNotAcknowledgedOrForwarded() {
        OrderCancelledRefundListener listener = new OrderCancelledRefundListener(refundCaseService);

        assertThatThrownBy(() -> listener.handleOrderCancelled(
                "{not-json}", "order.cancelled", 0, 1L, acknowledgment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid order.cancelled JSON");

        verify(refundCaseService, never()).processOrderCancellation(any());
        verify(acknowledgment, never()).acknowledge();
    }

    private OrderCancelledEvent event() {
        return OrderCancelledEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("ORDER_CANCELLED")
                .occurredAt(LocalDateTime.of(2026, 8, 2, 10, 0))
                .orderId(101L)
                .userId(7L)
                .restaurantId(11L)
                .previousStatus("ASSIGNED")
                .currentStatus("CANCELLED")
                .cancelReason("restaurant unavailable")
                .cancelledBy(7L)
                .paymentMethod("COD")
                .subtotalPrice(new BigDecimal("100000"))
                .discountAmount(new BigDecimal("5000"))
                .shippingFee(new BigDecimal("25000"))
                .totalPrice(new BigDecimal("120000"))
                .build();
    }

    private OrderCancelledRefundListener listener() {
        return new OrderCancelledRefundListener(refundCaseService);
    }
}
