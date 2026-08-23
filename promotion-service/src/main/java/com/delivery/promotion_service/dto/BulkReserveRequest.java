package com.delivery.promotion_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkReserveRequest {
    @NotNull
    private UUID reservationId;

    @NotNull
    @Positive
    private Long userId;

    @Positive
    private Long userPrincipalId;

    @NotNull
    @Positive
    private Long orderId;

    @NotNull
    @Positive
    private Long restaurantId;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal subtotal;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal grossShippingFee;

    @NotEmpty
    @Valid
    private List<@Positive Long> voucherIds;
}
