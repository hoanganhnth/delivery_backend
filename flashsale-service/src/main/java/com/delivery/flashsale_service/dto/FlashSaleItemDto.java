package com.delivery.flashsale_service.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class FlashSaleItemDto {
    private Long id;
    private Long campaignId;
    private Long restaurantId;
    private Long menuItemId;
    private BigDecimal originalPrice;
    private BigDecimal flashSalePrice;
    private Integer stockQuantity;
    private Integer soldQuantity;
    private String status;
}
