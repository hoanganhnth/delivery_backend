package com.delivery.promotion.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class LegacyDiscountCalculator {
    private LegacyDiscountCalculator() {}

    public static BigDecimal calculate(Voucher voucher, BigDecimal subtotal, BigDecimal shippingFee) {
        BigDecimal discount = switch (voucher.getRewardType()) {
            case FIXED -> voucher.getDiscountValue().min(subtotal);
            case PERCENTAGE -> subtotal.multiply(voucher.getDiscountValue())
                    .divide(BigDecimal.valueOf(100)).min(subtotal);
            case FREESHIP -> shippingFee.min(voucher.getDiscountValue());
        };
        if (voucher.getMaxDiscountValue() != null) discount = discount.min(voucher.getMaxDiscountValue());
        return discount.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
