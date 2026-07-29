package com.delivery.flashsale_service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ReserveItemRequest {
    @NotNull
    @Positive
    private Long flashSaleItemId;

    @NotNull
    @Positive
    private Integer quantity;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal price; // Passed from client to verify against DB
}
