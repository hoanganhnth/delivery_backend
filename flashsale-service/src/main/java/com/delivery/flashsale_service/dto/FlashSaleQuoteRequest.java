package com.delivery.flashsale_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
public class FlashSaleQuoteRequest {
    @NotNull @Positive private Long restaurantId;
    @NotEmpty private List<@Valid @NotNull ReserveItemRequest> items;
}
