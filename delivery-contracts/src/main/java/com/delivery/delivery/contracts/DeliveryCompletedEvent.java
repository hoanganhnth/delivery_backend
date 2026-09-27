package com.delivery.delivery.contracts;

import com.delivery.identity.contracts.SimulationContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Immutable delivery-completed event shared by delivery, settlement, restaurant, and shipper. */
public record DeliveryCompletedEvent(
        UUID eventId,
        String eventType,
        SimulationContext simulationContext,
        Long deliveryId,
        Long orderId,
        Long shipperId,
        Long restaurantId,
        BigDecimal shippingFee,
        BigDecimal grossShippingFee,
        BigDecimal customerShippingFee,
        BigDecimal subtotalPrice,
        BigDecimal shopDiscount,
        BigDecimal platformSubsidy,
        BigDecimal shippingDiscount,
        BigDecimal totalPrice,
        BigDecimal shipperEarnings,
        BigDecimal restaurantEarnings,
        BigDecimal restaurantCommission,
        BigDecimal shippingCommission,
        BigDecimal totalPlatformEarnings,
        BigDecimal platformCommission,
        LocalDateTime deliveredAt,
        LocalDateTime occurredAt,
        String deliveryAddress,
        String paymentMethod,
        String restaurantName,
        String customerName) {
}
