package com.delivery.restaurant_service.dto.event;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Event received from Delivery Service when delivery is completed
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryCompletedEvent {

    private Long deliveryId;
    private Long orderId;
    private Long restaurantId;
    private Long shipperId;

    private BigDecimal shippingFee; // Total shipping fee paid by customer
    private BigDecimal restaurantEarnings; // Amount restaurant earns from this order
    private BigDecimal shipperEarnings; // Amount shipper earns (85%)
    private BigDecimal platformCommission; // Platform commission (15%)

    private LocalDateTime deliveredAt;
    private String deliveryAddress;
    private String restaurantName;
    private String customerName;
}
