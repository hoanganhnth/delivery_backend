package com.delivery.flashsale_service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class ReserveItemRequest {
    @NotNull
    @Positive
    private Long flashSaleItemId;

    @NotNull
    @Positive
    private Integer quantity;

}
