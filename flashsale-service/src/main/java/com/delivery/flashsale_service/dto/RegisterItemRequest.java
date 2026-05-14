package com.delivery.flashsale_service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class RegisterItemRequest {
    @NotNull
    private Long campaignId;
    
    @NotNull
    private Long restaurantId;
    
    @NotNull
    private Long menuItemId;
    
    @NotNull
    @Min(0)
    private BigDecimal originalPrice;
    
    @NotNull
    @Min(0)
    private BigDecimal flashSalePrice;
    
    @NotNull
    @Min(1)
    private Integer stockQuantity;
}
