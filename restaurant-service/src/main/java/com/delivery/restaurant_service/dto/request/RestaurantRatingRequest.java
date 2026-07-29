package com.delivery.restaurant_service.dto.request;

import lombok.Data;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Data
public class RestaurantRatingRequest {
    @NotNull
    @Min(1)
    private Long orderId;

    @NotNull
    @Min(1)
    @Max(5)
    private Integer rating;

    @Size(max = 2000)
    private String comment;
}
