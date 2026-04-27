package com.delivery.delivery_service.dto.event;

import lombok.*;

import java.math.BigDecimal;

/**
 * ✅ Event được publish khi shipper lấy hàng xong (PICKED_UP)
 * Settlement Service sẽ listen:
 *   - COD: trừ ví shipper (COD_DEDUCTION)
 *   - Pre-paid: bỏ qua
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryPickedUpEvent {

    private Long deliveryId;
    private Long orderId;
    private Long shipperId;
    private Long restaurantId;
    private String paymentMethod;       // "COD" or "ONLINE"
    private BigDecimal totalPrice;      // Tổng tiền khách trả
    private BigDecimal shippingFee;     // Phí ship
    private BigDecimal shipperEarnings;     // Shipper thực nhận (85% SF)
    private BigDecimal platformCommission;  // Hoa hồng nền tảng (15% SF)
}
