package com.delivery.settlement_service.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event received from Kafka when delivery is completed.
 * Must match the fields published by delivery-service.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryCompletedEvent {
    private UUID eventId;
    private String eventType;
    private LocalDateTime occurredAt;
    private Long deliveryId;
    private Long orderId;
    private Long restaurantId;
    private Long shipperId;
    private BigDecimal restaurantEarnings;
    private BigDecimal shipperEarnings;
    private BigDecimal restaurantCommission;
    private BigDecimal shippingCommission;
    private BigDecimal totalPlatformEarnings;
    private BigDecimal shippingFee;
    private LocalDateTime deliveredAt;
    private String deliveryAddress;
    private String paymentMethod;     // "COD" or "ONLINE"
    private String restaurantName;
    private String customerName;
}
