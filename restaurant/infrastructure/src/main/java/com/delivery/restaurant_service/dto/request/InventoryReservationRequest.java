package com.delivery.restaurant_service.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReservationRequest {

    @NotNull
    private UUID reservationId;

    @NotNull
    @Positive
    private Long orderId;

    @Positive
    private Long userId;

    @Positive
    private Long userPrincipalId;

    @NotNull
    @Positive
    private Long restaurantId;

    @NotEmpty
    @Valid
    private List<Line> items;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Line {
        @NotNull
        @Positive
        private Long menuItemId;

        @NotNull
        @Positive
        private Integer quantity;
    }
}
