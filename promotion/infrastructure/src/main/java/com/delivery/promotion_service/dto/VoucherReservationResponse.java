package com.delivery.promotion_service.dto;

import com.delivery.promotion_service.entity.VoucherReservation;
import com.delivery.promotion_service.entity.Voucher;
import com.delivery.promotion_service.service.VoucherLayer;
import com.delivery.promotion_service.service.VoucherLayerResolver;
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
    private String layer;
    private String fundingSource;
    private BigDecimal discountBase;
    private BigDecimal itemDiscount;
    private BigDecimal shippingDiscount;
    private BigDecimal customerShippingFee;
    private BigDecimal grossShippingFee;
    private BigDecimal platformSubsidy;
    private BigDecimal shopDiscount;
    private VoucherReservation.State state;
    private LocalDateTime expiresAt;

    public static VoucherReservationResponse from(VoucherReservation reservation) {
        return from(reservation, null);
    }

    public static VoucherReservationResponse from(VoucherReservation reservation, Voucher voucher) {
        // Terminal recovery must remain readable for pre-stacking MERCHANT rows,
        // even though those rows are no longer eligible for a new checkout.
        VoucherLayer resolvedLayer = legacyCompatibleLayer(voucher);
        String layer = resolvedLayer == null ? null : resolvedLayer.name();
        String fundingSource = resolvedLayer == null ? null
                : resolvedLayer == VoucherLayer.SHOP_DISCOUNT ? "SHOP" : "PLATFORM";
        BigDecimal discount = reservation.getDiscountAmount();
        BigDecimal shippingDiscount = resolvedLayer == VoucherLayer.FREESHIP ? discount : BigDecimal.ZERO;
        BigDecimal itemDiscount = resolvedLayer == VoucherLayer.FREESHIP ? BigDecimal.ZERO : discount;
        BigDecimal customerShipping = reservation.getShippingFee().subtract(shippingDiscount).max(BigDecimal.ZERO);
        BigDecimal platformSubsidy = "SHOP".equalsIgnoreCase(fundingSource) ? BigDecimal.ZERO : discount;
        BigDecimal shopDiscount = "SHOP".equalsIgnoreCase(fundingSource) ? discount : BigDecimal.ZERO;
        return VoucherReservationResponse.builder()
                .reservationId(reservation.getReservationId())
                .orderId(reservation.getOrderId())
                .voucherId(reservation.getVoucherId())
                .discountAmount(discount)
                .layer(layer)
                .fundingSource(fundingSource)
                .discountBase(resolvedLayer == VoucherLayer.FREESHIP
                        ? reservation.getShippingFee() : reservation.getSubtotal())
                .itemDiscount(itemDiscount)
                .shippingDiscount(shippingDiscount)
                .customerShippingFee(customerShipping)
                .grossShippingFee(reservation.getShippingFee())
                .platformSubsidy(platformSubsidy)
                .shopDiscount(shopDiscount)
                .state(reservation.getState())
                .expiresAt(reservation.getExpiresAt())
                .build();
    }

    private static VoucherLayer legacyCompatibleLayer(Voucher voucher) {
        if (voucher == null || voucher.getRewardType() == null) return null;
        if (voucher.getRewardType() == Voucher.RewardType.FREESHIP) return VoucherLayer.FREESHIP;
        if (voucher.getCreatorType() == Voucher.CreatorType.MERCHANT
                || voucher.getCreatorType() == Voucher.CreatorType.SHOP) {
            return VoucherLayer.SHOP_DISCOUNT;
        }
        try {
            return VoucherLayerResolver.resolve(voucher);
        } catch (IllegalArgumentException ignored) {
            return VoucherLayer.PLATFORM_DISCOUNT;
        }
    }
}
