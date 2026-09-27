package com.delivery.order.contracts;

import com.delivery.identity.contracts.SimulationContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Immutable order-created snapshot shared with fulfilment consumers. */
public record OrderCreatedEvent(
        Integer schemaVersion,
        UUID eventId,
        Long orderId,
        Long userId,
        Long userPrincipalId,
        Long restaurantId,
        String status,
        BigDecimal subtotalPrice,
        BigDecimal discountAmount,
        BigDecimal shippingFee,
        BigDecimal totalPrice,
        BigDecimal itemDiscount,
        BigDecimal shippingDiscount,
        BigDecimal customerShippingFee,
        BigDecimal grossShippingFee,
        BigDecimal platformSubsidy,
        BigDecimal shopDiscount,
        String paymentMethod,
        String deliveryAddress,
        Double deliveryLat,
        Double deliveryLng,
        Double pickupLat,
        Double pickupLng,
        String restaurantName,
        String restaurantAddress,
        String restaurantPhone,
        String customerName,
        String customerPhone,
        String notes,
        LocalDateTime createdAt,
        Long creatorId,
        Long creatorPrincipalId,
        UUID voucherReservationId,
        UUID promotionReservationId,
        UUID flashSaleReservationId,
        UUID inventoryReservationId,
        List<Map<String, Object>> items,
        List<Map<String, Object>> appliedVouchers,
        SimulationContext simulationContext,
        String eventType,
        LocalDateTime eventTimestamp) {
}
