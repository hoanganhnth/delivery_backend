package com.delivery.flashsale_service.dto;

import com.delivery.flashsale_service.entity.FlashSaleReservation;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data @Builder
public class FlashSaleReservationResponse {
    private UUID reservationId;
    private Long orderId;
    private FlashSaleReservation.State state;
    private LocalDateTime expiresAt;
    private List<Line> items;

    @Data @Builder
    public static class Line {
        private Long flashSaleItemId;
        private Long menuItemId;
        private Integer quantity;
        private BigDecimal unitPrice;
    }

    public static FlashSaleReservationResponse from(FlashSaleReservation reservation) {
        return FlashSaleReservationResponse.builder()
                .reservationId(reservation.getReservationId()).orderId(reservation.getOrderId())
                .state(reservation.getState()).expiresAt(reservation.getExpiresAt())
                .items(reservation.getLines().stream().map(line -> Line.builder()
                        .flashSaleItemId(line.getFlashSaleItemId()).menuItemId(line.getMenuItemId())
                        .quantity(line.getQuantity()).unitPrice(line.getUnitPrice()).build()).toList())
                .build();
    }
}
