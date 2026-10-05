package com.delivery.promotion_service.service;

import com.delivery.promotion_service.dto.VoucherSelectionMode;
import com.delivery.promotion_service.entity.Voucher;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public final class VoucherStackingCalculator {
    private final com.delivery.promotion.application.CalculateVouchersUseCase calculator =
            new com.delivery.promotion.application.CalculateVouchersUseCase();

    public Calculation calculate(Collection<Voucher> vouchers, Long restaurantId, BigDecimal subtotal,
                                 BigDecimal grossShippingFee, Collection<Long> selectedVoucherIds,
                                 VoucherSelectionMode selectionMode, LocalDateTime now) {
        var result = calculator.calculate(new com.delivery.promotion.application.api.PromotionCommands.Pricing(
                vouchers == null ? null : vouchers.stream().map(VoucherDomainMapper::snapshot).toList(),
                restaurantId, subtotal, grossShippingFee, selectedVoucherIds,
                selectionMode == null ? null : com.delivery.promotion.domain.VoucherSelectionMode.valueOf(selectionMode.name()),
                now));
        return from(result);
    }

    static Calculation from(com.delivery.promotion.domain.VoucherStackingCalculator.Calculation result) {
        return new Calculation(result.appliedVouchers().stream().map(applied -> new AppliedVoucher(
                applied.voucherId(), applied.code(), VoucherLayer.valueOf(applied.layer().name()),
                applied.discountAmount(), applied.discountBase(), applied.fundingSource())).toList(),
                result.unavailableVouchers().stream().map(unavailable -> new UnavailableVoucher(
                        unavailable.voucherId(), unavailable.code(), unavailable.reason())).toList(),
                result.itemDiscount(), result.shippingDiscount(), result.totalDiscount(),
                result.customerShippingFee(), result.totalAmount());
    }

    public record AppliedVoucher(
            Long voucherId,
            String code,
            VoucherLayer layer,
            BigDecimal discountAmount,
            BigDecimal discountBase,
            String fundingSource) {
    }

    public record UnavailableVoucher(Long voucherId, String code, String reason) {
    }

    public record Calculation(
            List<AppliedVoucher> appliedVouchers,
            List<UnavailableVoucher> unavailableVouchers,
            BigDecimal itemDiscount,
            BigDecimal shippingDiscount,
            BigDecimal totalDiscount,
            BigDecimal customerShippingFee,
            BigDecimal totalAmount) {
        public Calculation {
            appliedVouchers = List.copyOf(appliedVouchers);
            unavailableVouchers = List.copyOf(unavailableVouchers);
        }
    }
}
