package com.delivery.flashsale_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class RegisterItemRequest {
    @NotNull
    @Positive
    private Long campaignId;
    
    @NotNull
    @Positive
    private Long restaurantId;
    
    @NotNull
    @Positive
    private Long menuItemId;
    
    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal originalPrice;
    
    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal flashSalePrice;
    
    @NotNull
    @Positive
    private Integer stockQuantity;
}
