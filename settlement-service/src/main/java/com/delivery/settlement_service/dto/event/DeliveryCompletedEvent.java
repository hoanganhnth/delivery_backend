package com.delivery.settlement_service.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Event received from Kafka when delivery is completed
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryCompletedEvent {
    private Long deliveryId;
    private Long orderId;
    private Long restaurantId;
    private Long shipperId;
    private BigDecimal restaurantEarnings;
    private BigDecimal shipperEarnings;
    private BigDecimal platformCommission;
    private BigDecimal shippingFee;
}
