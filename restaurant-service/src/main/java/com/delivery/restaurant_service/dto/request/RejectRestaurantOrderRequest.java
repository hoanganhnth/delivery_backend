package com.delivery.restaurant_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RejectRestaurantOrderRequest {

    @NotNull
    @Positive
    private Long restaurantId;

    @NotBlank
    @Size(max = 500)
    private String reason;
}
