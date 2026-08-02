package com.delivery.settlement_service.service;

import com.delivery.settlement_service.dto.event.OrderCancelledEvent;
import com.delivery.settlement_service.dto.response.RefundCaseResponse;
import com.delivery.settlement_service.entity.RefundCase;
import com.delivery.settlement_service.repository.RefundCaseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundCaseServiceTest {
    @Mock RefundCaseRepository repository;
    @Mock RefundOutboxService outboxService;

    private RefundCaseService service;

    @BeforeEach
    void setUp() {
        service = new RefundCaseService(repository, outboxService, new ObjectMapper(), false);
        lenient().when(repository.findByEventId(any())).thenReturn(Optional.empty());
        lenient().when(repository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        lenient().when(repository.findByOrderIdAndTriggerAndComponent(any(), any(), any())).thenReturn(Optional.empty());
        lenient().when(repository.saveAndFlush(any(RefundCase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void codCancellationBeforePickupCreatesNoRefundRequiredCase() {
        RefundCase result = service.processOrderCancellation(event("COD", "ASSIGNED"));

        assertThat(result.getStatus()).isEqualTo(RefundCase.RefundStatus.NO_REFUND_REQUIRED);
        assertThat(result.getCapturedAmount()).isEqualByComparingTo("0");
        assertThat(result.getRefundAmount()).isEqualByComparingTo("0");
        verify(outboxService, never()).enqueue(any());
    }

    @Test
    void onlineCancellationFailsClosedToManualReviewWhileProviderIsDisabled() {
        RefundCase result = service.processOrderCancellation(event("ONLINE", "CONFIRMED"));

        assertThat(result.getStatus()).isEqualTo(RefundCase.RefundStatus.MANUAL_REVIEW);
        assertThat(result.getCapturedAmount()).isEqualByComparingTo("120000");
        assertThat(result.getRefundAmount()).isEqualByComparingTo("120000");
        verify(outboxService, never()).enqueue(any());
    }

    @Test
    void postPickupCancellationRequiresManualReviewAndNeverCreatesNegativeCodRefund() {
        RefundCase result = service.processOrderCancellation(event("COD", "PICKED_UP"));

        assertThat(result.getStatus()).isEqualTo(RefundCase.RefundStatus.MANUAL_REVIEW);
        assertThat(result.getCapturedAmount()).isEqualByComparingTo("0");
        assertThat(result.getRefundAmount()).isEqualByComparingTo("0");
    }

    @Test
    void exactEventReplayReturnsExistingCaseWithoutWritingOrEnqueueing() {
        OrderCancelledEvent event = event("COD", "ASSIGNED");
        RefundCase existing = service.processOrderCancellation(event);
        when(repository.findByEventId(event.getEventId())).thenReturn(Optional.of(existing));

        RefundCase replay = service.processOrderCancellation(event);

        assertThat(replay).isSameAs(existing);
        verify(repository).saveAndFlush(any(RefundCase.class));
        verify(outboxService, never()).enqueue(any());
    }

    @Test
    void conflictingReplayWithSameEventIdFailsClosed() {
        OrderCancelledEvent event = event("COD", "ASSIGNED");
        RefundCase existing = service.processOrderCancellation(event);
        OrderCancelledEvent conflicting = event("COD", "ASSIGNED");
        conflicting.setEventId(event.getEventId());
        conflicting.setCancelReason("different reason");
        when(repository.findByEventId(event.getEventId())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.processOrderCancellation(conflicting))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contradictory");
    }

    @Test
    void inconsistentMoneySnapshotIsRejectedBeforePersistence() {
        OrderCancelledEvent event = event("COD", "PENDING");
        event.setTotalPrice(new BigDecimal("119000"));

        assertThatThrownBy(() -> service.processOrderCancellation(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("monetary snapshot");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void adminListReturnsReadOnlyProjectionAndCapsCompatibilityLimit() {
        RefundCase existing = persistedCase();
        when(repository.findByStatusOrderByCreatedAtDesc(
                eq(RefundCase.RefundStatus.MANUAL_REVIEW), any()))
                .thenReturn(List.of(existing));

        List<RefundCaseResponse> result = service.listAdminCases(
                RefundCase.RefundStatus.MANUAL_REVIEW, 1000);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRefundId()).isEqualTo(existing.getRefundId());
        assertThat(result.get(0).getStatus()).isEqualTo("MANUAL_REVIEW");
        assertThat(result.get(0).getRefundAmount()).isEqualByComparingTo(existing.getRefundAmount());
        verify(repository).findByStatusOrderByCreatedAtDesc(
                eq(RefundCase.RefundStatus.MANUAL_REVIEW),
                org.mockito.ArgumentMatchers.argThat(page -> page.getPageSize() == 100));
    }

    @Test
    void adminGetMissingCaseFailsClosed() {
        UUID refundId = UUID.randomUUID();
        when(repository.findById(refundId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAdminCase(refundId))
                .isInstanceOf(com.delivery.settlement_service.exception.ResourceNotFoundException.class)
                .hasMessageContaining(refundId.toString());
    }

    @Test
    void adminListWithoutStatusUsesNewestCases() {
        when(repository.findAllByOrderByCreatedAtDesc(any())).thenReturn(List.of(persistedCase()));

        assertThatCode(() -> service.listAdminCases(null, 0)).doesNotThrowAnyException();
        verify(repository).findAllByOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.argThat(page -> page.getPageSize() == 1));
    }

    private OrderCancelledEvent event(String paymentMethod, String previousStatus) {
        return OrderCancelledEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("ORDER_CANCELLED")
                .occurredAt(LocalDateTime.of(2026, 8, 2, 10, 0))
                .orderId(101L)
                .userId(7L)
                .restaurantId(11L)
                .previousStatus(previousStatus)
                .currentStatus("CANCELLED")
                .cancelReason("restaurant unavailable")
                .cancelledBy(7L)
                .cancelledAt(LocalDateTime.of(2026, 8, 2, 10, 0))
                .paymentMethod(paymentMethod)
                .subtotalPrice(new BigDecimal("100000"))
                .discountAmount(new BigDecimal("5000"))
                .shippingFee(new BigDecimal("25000"))
                .totalPrice(new BigDecimal("120000"))
                .build();
    }

    private RefundCase persistedCase() {
        return RefundCase.builder()
                .refundId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
                .eventId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"))
                .idempotencyKey("101:ORDER_CANCELLED:ORDER_TOTAL")
                .orderId(101L)
                .userId(7L)
                .restaurantId(11L)
                .previousOrderStatus("PICKED_UP")
                .currentOrderStatus("CANCELLED")
                .paymentMethod("ONLINE")
                .trigger(RefundCase.RefundTrigger.ORDER_CANCELLED)
                .component(RefundCase.RefundComponent.ORDER_TOTAL)
                .status(RefundCase.RefundStatus.MANUAL_REVIEW)
                .currency("VND")
                .subtotalAmount(new BigDecimal("100000"))
                .discountAmount(new BigDecimal("5000"))
                .shippingFee(new BigDecimal("25000"))
                .totalAmount(new BigDecimal("120000"))
                .capturedAmount(new BigDecimal("120000"))
                .refundAmount(new BigDecimal("120000"))
                .actorSource("ACTOR")
                .actorId(7L)
                .reason("customer dispute")
                .payloadFingerprint("a".repeat(64))
                .attempts(0)
                .createdAt(LocalDateTime.of(2026, 8, 2, 10, 0))
                .updatedAt(LocalDateTime.of(2026, 8, 2, 10, 0))
                .build();
    }
}
