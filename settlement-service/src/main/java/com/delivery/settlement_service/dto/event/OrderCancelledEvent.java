package com.delivery.settlement_service.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
    private String cancelledBySource;
    private String cancelReasonCode;
    private LocalDateTime cancelledAt;

    private Long shipperId;
    private Boolean hasActiveDelivery;

    private UUID voucherReservationId;
    private UUID flashSaleReservationId;
    private List<Map<String, Object>> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private BigDecimal subtotalPrice;
    private BigDecimal discountAmount;
    private BigDecimal shippingFee;
    private BigDecimal totalPrice;
    private String paymentMethod;
}
