package com.delivery.delivery_service.dto.event;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ✅ Event được publish khi delivery hoàn thành
 * Shipper Service sẽ listen để tự động cộng tiền vào balance
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryCompletedEvent {
    
    private Long deliveryId;
    private Long orderId;
    private Long shipperId;
    private Long restaurantId;
    
    private BigDecimal shippingFee; // Tổng phí ship khách hàng trả
    private BigDecimal shipperEarnings; // Số tiền shipper thực nhận (85% của shippingFee)
    private BigDecimal platformCommission; // Commission platform lấy (15%)
    
    private LocalDateTime deliveredAt;
    private String deliveryAddress;
    
    // Additional info for transaction description
    private String restaurantName;
    private String customerName;
}
