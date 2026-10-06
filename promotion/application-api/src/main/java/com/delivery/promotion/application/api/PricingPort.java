package com.delivery.promotion.application.api;

import com.delivery.promotion.domain.VoucherStackingCalculator;

/** Validation, wallet reads and wire projection remain adapter operations. */
public interface PricingPort<C, R> {
    void validate();
    C loadWalletVouchers();
    PromotionCommands.Pricing pricing(C context);
    R result(C context, VoucherStackingCalculator.Calculation calculation);
}
