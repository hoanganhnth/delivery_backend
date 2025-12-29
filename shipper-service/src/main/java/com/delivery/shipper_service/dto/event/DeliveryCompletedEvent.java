package com.delivery.shipper_service.dto.event;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ✅ Event nhận từ Delivery Service khi giao hàng hoàn thành
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
    
    private BigDecimal shippingFee; // Tổng phí ship khách hàng trả
    private BigDecimal shipperEarnings; // Số tiền shipper thực nhận (85%)
    private BigDecimal platformCommission; // Commission platform (15%)
    
    private LocalDateTime deliveredAt;
    private String deliveryAddress;
    private String restaurantName;
    private String customerName;
}
