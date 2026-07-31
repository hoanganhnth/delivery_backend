package com.delivery.flashsale_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class FlashSaleReservationRequest {
    @NotNull private UUID reservationId;
    @NotNull @Positive private Long orderId;
    @NotNull @Positive private Long userId;
    @NotNull @Positive private Long restaurantId;
    @NotEmpty private List<@Valid @NotNull ReserveItemRequest> items;
}
