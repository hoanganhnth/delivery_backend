package com.delivery.promotion_service.dto;

import com.delivery.promotion_service.entity.VoucherReservation;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class VoucherReservationResponse {
    private UUID reservationId;
    private Long orderId;
    private Long voucherId;
    private BigDecimal discountAmount;
    private VoucherReservation.State state;
    private LocalDateTime expiresAt;

    public static VoucherReservationResponse from(VoucherReservation reservation) {
        return VoucherReservationResponse.builder()
                .reservationId(reservation.getReservationId())
                .orderId(reservation.getOrderId())
                .voucherId(reservation.getVoucherId())
                .discountAmount(reservation.getDiscountAmount())
                .state(reservation.getState())
                .expiresAt(reservation.getExpiresAt())
                .build();
    }
}
