package com.delivery.promotion.application;

import com.delivery.promotion.application.api.PromotionCommands;
import com.delivery.promotion.application.api.PricingPort;
import com.delivery.promotion.domain.VoucherStackingCalculator;

public final class CalculateVouchersUseCase {
    private final VoucherStackingCalculator calculator = new VoucherStackingCalculator();
    public <C, R> R calculate(PricingPort<C, R> port) {
        port.validate();
        C context = port.loadWalletVouchers();
        var calculation = calculate(port.pricing(context));
        return port.result(context, calculation);
    }
    public VoucherStackingCalculator.Calculation calculate(PromotionCommands.Pricing command) {
        return calculator.calculate(command.vouchers(), command.restaurantId(), command.subtotal(),
                command.shipping(), command.selectedIds(), command.mode(), command.now());
    }
}
