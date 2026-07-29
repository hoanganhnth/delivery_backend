package com.delivery.restaurant_service.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ConfirmRestaurantOrderRequest {

    @NotNull
    @Positive
    private Long restaurantId;

    @NotNull
    @Min(1)
    @Max(240)
    private Integer estimatedPrepTime;

    @Size(max = 500)
    private String notes;
}
