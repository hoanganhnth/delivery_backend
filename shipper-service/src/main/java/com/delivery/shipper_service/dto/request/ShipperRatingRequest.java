package com.delivery.shipper_service.dto.request;

import lombok.Data;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Data
public class ShipperRatingRequest {
    @NotNull
    @Positive
    private Long orderId;
    @NotNull
    @Min(1)
    @Max(5)
    private Integer rating;
    @Size(max = 500)
    private String comment;
}
