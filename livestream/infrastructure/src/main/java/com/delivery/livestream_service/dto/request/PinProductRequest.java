package com.delivery.livestream_service.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PinProductRequest {

    @NotNull(message = "Product ID không được để trống")
    @Positive(message = "Product ID phải lớn hơn 0")
    private Long productId;

    @NotNull(message = "Giá không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá phải lớn hơn 0")
    private BigDecimal priceAtLive;

    private String productName;
    private String productImage;
    private Long restaurantId;
    private String restaurantName;
}
