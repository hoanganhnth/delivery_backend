package com.delivery.flashsale_service.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ReserveItemRequest {
    @NotNull
    private Long flashSaleItemId;

    @NotNull
    private Integer quantity;

    @NotNull
    private BigDecimal price; // Passed from client to verify against DB
}
