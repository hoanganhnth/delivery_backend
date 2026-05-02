package com.delivery.promotion_service.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartContextRequest {
    private Long shopId;
    private Long userId;
    private BigDecimal subTotal;
    private BigDecimal shippingFee;
    // Assuming a simplified cart representation
    // private List<CartItem> items; 
}
