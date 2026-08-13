package com.delivery.settlement_service.service;

import com.delivery.settlement_service.dto.event.OrderCancelledEvent;
import com.delivery.settlement_service.dto.response.RefundCaseResponse;
import com.delivery.settlement_service.dto.response.RefundCustomerCaseResponse;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
        lenient().when(repository.insertIfAbsentPostgres(
                any(), any(), any(), anyLong(), anyLong(), anyLong(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(1);
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
    void noShipperCodOutcomeUsesDedicatedTriggerAndNeedsNoCustomerRefund() {
        OrderCancelledEvent event = event("COD", "FINDING_SHIPPER");
        event.setEventType("REFUND_ELIGIBLE");
        event.setCurrentStatus("SHIPPER_NOT_FOUND");
        event.setCancelledBy(null);
        event.setCancelledBySource("SYSTEM");
        event.setCancelReasonCode("SHIPPER_NOT_FOUND");

        RefundCase result = service.processOrderCancellation(event);

        assertThat(result.getTrigger()).isEqualTo(RefundCase.RefundTrigger.SHIPPER_NOT_FOUND);
        assertThat(result.getStatus()).isEqualTo(RefundCase.RefundStatus.NO_REFUND_REQUIRED);
        assertThat(result.getRefundAmount()).isEqualByComparingTo("0");
        verify(outboxService, never()).enqueue(any());
    }

    @Test
    void systemPaymentFailureCanRequestOnlineRefundOnlyWhenProviderBoundaryIsEnabled() {
        RefundCaseService providerEnabled = new RefundCaseService(
                repository, outboxService, new ObjectMapper(), true);
        OrderCancelledEvent event = event("ONLINE", "PENDING");
        event.setCancelledBySource("SYSTEM");
        event.setCancelReasonCode("PAYMENT_FAILED");

        RefundCase result = providerEnabled.processOrderCancellation(event);

        assertThat(result.getTrigger()).isEqualTo(RefundCase.RefundTrigger.PAYMENT_FAILED);
        assertThat(result.getStatus()).isEqualTo(RefundCase.RefundStatus.REQUESTED);
        assertThat(result.getRefundAmount()).isEqualByComparingTo("120000");
        verify(outboxService).enqueue(result);
    }

    @Test
    void confirmedCustomerCancellationNeverBecomesAutomaticProviderRefund() {
        RefundCaseService providerEnabled = new RefundCaseService(
                repository, outboxService, new ObjectMapper(), true);
        OrderCancelledEvent event = event("ONLINE", "CONFIRMED");
        event.setCancelledBySource("CUSTOMER");
        event.setCancelReasonCode("CUSTOMER_CANCELLED");

        RefundCase result = providerEnabled.processOrderCancellation(event);

        assertThat(result.getStatus()).isEqualTo(RefundCase.RefundStatus.MANUAL_REVIEW);
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
        verify(repository).insertIfAbsentPostgres(
                any(), any(), any(), anyLong(), anyLong(), anyLong(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt());
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
        verify(repository, never()).insertIfAbsentPostgres(
                any(), any(), any(), anyLong(), anyLong(), anyLong(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt());
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

    @Test
    void customerListIsScopedToTrustedUserAndUsesSafeProjection() {
        RefundCase existing = persistedCase();
        when(repository.findByUserIdOrderByCreatedAtDesc(eq(7L), any()))
                .thenReturn(List.of(existing));

        List<RefundCustomerCaseResponse> result = service.listCustomerCases(7L, 50);

        assertThat(result).singleElement().satisfies(response -> {
            assertThat(response.getRefundId()).isEqualTo(existing.getRefundId());
            assertThat(response.getOrderId()).isEqualTo(existing.getOrderId());
            assertThat(response.getPaymentMethod()).isEqualTo("ONLINE");
            assertThat(response.getTrigger()).isEqualTo("ORDER_CANCELLED");
            assertThat(response.getStatus()).isEqualTo("MANUAL_REVIEW");
            assertThat(response.getCurrency()).isEqualTo("VND");
            assertThat(response.getRefundAmount()).isEqualByComparingTo("120000");
            assertThat(response.getCreatedAt()).isEqualTo(existing.getCreatedAt());
            assertThat(response.getUpdatedAt()).isEqualTo(existing.getUpdatedAt());
            assertThat(response.getProcessedAt()).isNull();
        });
        assertThat(RefundCustomerCaseResponse.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("eventId", "idempotencyKey", "actorSource", "actorId",
                        "reason", "providerReference", "lastError", "attempts");
        verify(repository).findByUserIdOrderByCreatedAtDesc(eq(7L),
                org.mockito.ArgumentMatchers.argThat(page -> page.getPageSize() == 50));
    }

    @Test
    void customerListCapsLimitAtOneHundred() {
        when(repository.findByUserIdOrderByCreatedAtDesc(eq(7L), any())).thenReturn(List.of());

        service.listCustomerCases(7L, 1_000);

        verify(repository).findByUserIdOrderByCreatedAtDesc(eq(7L),
                org.mockito.ArgumentMatchers.argThat(page -> page.getPageSize() == 100));
    }

    @Test
    void customerListRejectsMissingOrNonPositiveIdentity() {
        assertThatThrownBy(() -> service.listCustomerCases(null, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
        assertThatThrownBy(() -> service.listCustomerCases(0L, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");

        verifyNoInteractions(repository);
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
