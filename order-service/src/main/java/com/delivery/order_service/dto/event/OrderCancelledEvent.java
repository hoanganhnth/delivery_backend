package com.delivery.order_service.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ✅ Event DTO được gửi qua Kafka khi order bị hủy theo Backend Instructions
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancelledEvent {
    
    // Order basic info
    private Long orderId;
    private Long userId;
    private Long restaurantId;
    private String previousStatus;
    private String currentStatus; // CANCELLED
    
    // Cancellation info
    private String cancelReason;
    private Long cancelledBy; // userId who cancelled
    /** CUSTOMER, RESTAURANT, ADMIN or SYSTEM; used by refund eligibility rules. */
    private String cancelledBySource;
    /** Stable business reason code, not a free-form UI message. */
    private String cancelReasonCode;
    private LocalDateTime cancelledAt;
    
    // Delivery related
    private Long shipperId; // null if no shipper assigned
    private Boolean hasActiveDelivery;
    private UUID voucherReservationId;
    private UUID flashSaleReservationId;

    // Immutable monetary snapshot used by settlement/refund consumers.
    private BigDecimal subtotalPrice;
    private BigDecimal discountAmount;
    private BigDecimal shippingFee;
    private BigDecimal totalPrice;
    private String paymentMethod;
    
    // Items
    private java.util.List<java.util.Map<String, Object>> items;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
