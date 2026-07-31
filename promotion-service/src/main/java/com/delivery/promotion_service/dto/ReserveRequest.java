package com.delivery.promotion_service.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReserveRequest {
    @NotNull
    private UUID reservationId;
    @NotNull
    @Positive
    private Long userId;
    @NotNull
    @Positive
    private Long orderId;
    @NotNull
    @Positive
    private Long voucherId;
    @NotNull
    @Positive
    private Long restaurantId;
    @NotNull
    @DecimalMin("0.0")
    private BigDecimal subtotal;
    @NotNull
    @DecimalMin("0.0")
    private BigDecimal shippingFee;
}
