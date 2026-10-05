package com.delivery.promotion_service.dto;

import com.delivery.promotion_service.entity.PromotionReservation;
import com.delivery.promotion_service.entity.PromotionReservationLine;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record PromotionReservationResponse(
        UUID reservationId,
        Long orderId,
        PromotionReservation.State state,
        LocalDateTime expiresAt,
        BigDecimal itemDiscount,
        BigDecimal shippingDiscount,
        BigDecimal totalDiscount,
        BigDecimal customerShippingFee,
        List<Line> lines) {

    public static PromotionReservationResponse from(PromotionReservation reservation,
                                                     List<PromotionReservationLine> lines) {
        return new PromotionReservationResponse(
                reservation.getReservationId(), reservation.getOrderId(), reservation.getState(),
                reservation.getExpiresAt(), reservation.getItemDiscount(), reservation.getShippingDiscount(),
                reservation.getTotalDiscount(), reservation.getCustomerShippingFee(),
                lines.stream().map(Line::from).toList());
    }

    @Builder
    public record Line(Long voucherId, String voucherCode, String layer, String fundingSource,
                       BigDecimal discountBase, BigDecimal discountAmount,
                       PromotionReservationLine.State state) {
        static Line from(PromotionReservationLine line) {
            return new Line(line.getVoucherId(), line.getVoucherCode(), line.getLayer(),
                    line.getFundingSource(), line.getDiscountBase(), line.getDiscountAmount(), line.getState());
        }
    }
}
