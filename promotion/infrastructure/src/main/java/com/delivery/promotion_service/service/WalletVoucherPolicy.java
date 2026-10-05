package com.delivery.promotion_service.service;

import com.delivery.promotion_service.entity.Voucher;
import java.math.BigDecimal;
import java.time.LocalDateTime;

final class WalletVoucherPolicy {
    private WalletVoucherPolicy() {}

    static boolean isApproved(Voucher voucher) {
        return com.delivery.promotion.domain.WalletVoucherPolicy.isApproved(VoucherDomainMapper.snapshot(voucher));
    }

    static boolean isCheckoutEligible(Voucher voucher) {
        return com.delivery.promotion.domain.WalletVoucherPolicy.isCheckoutEligible(VoucherDomainMapper.snapshot(voucher));
    }

    static void requireCollectable(Voucher voucher, LocalDateTime now) {
        String failure = com.delivery.promotion.domain.WalletVoucherPolicy.collectionUnavailableReason(
                VoucherDomainMapper.snapshot(voucher), now);
        if (failure != null) throw new IllegalArgumentException(failure);
    }

    static String reservationUnavailableReason(Voucher voucher, Long restaurantId,
                                               BigDecimal subtotal, LocalDateTime now) {
        return com.delivery.promotion.domain.WalletVoucherPolicy.reservationUnavailableReason(
                VoucherDomainMapper.snapshot(voucher), restaurantId, subtotal, now);
    }
}
