package com.delivery.settlement_service.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Event received when shipper picks up order.
 * Used for COD deduction — trừ ví shipper khi lấy hàng tiền mặt.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryPickedUpEvent {
    private Long deliveryId;
    private Long orderId;
    private Long shipperId;
    private Long restaurantId;
    private String paymentMethod;    // "COD" or "ONLINE"
    private BigDecimal totalPrice;   // Tổng tiền khách trả
    private BigDecimal shippingFee;  // Phí ship
    private BigDecimal shipperEarnings;     // Shipper thực nhận (85% SF)
    private BigDecimal platformCommission;  // Hoa hồng nền tảng (15% SF)
}
