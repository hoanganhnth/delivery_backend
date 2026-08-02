package com.delivery.settlement_service.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Canonical cancellation snapshot consumed by the refund boundary. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancelledEvent {
    private UUID eventId;
    private String eventType;
    private LocalDateTime occurredAt;

    private Long orderId;
    private Long userId;
    private Long restaurantId;
    private String previousStatus;
    private String currentStatus;

    private String cancelReason;
    private Long cancelledBy;
    private LocalDateTime cancelledAt;

    private Long shipperId;
    private Boolean hasActiveDelivery;

    private UUID voucherReservationId;
    private UUID flashSaleReservationId;
    private BigDecimal subtotalPrice;
    private BigDecimal discountAmount;
    private BigDecimal shippingFee;
    private BigDecimal totalPrice;
    private String paymentMethod;
}
