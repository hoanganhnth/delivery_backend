package com.delivery.promotion_service.service;

import com.delivery.promotion_service.dto.VoucherSelectionMode;
import com.delivery.promotion_service.entity.Voucher;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VoucherStackingCalculatorTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 22, 12, 0);
    private final VoucherStackingCalculator calculator = new VoucherStackingCalculator();

    @Test
    void autoModeChoosesBestThreeLayerCombinationAndAppliesInOrder() {
        Voucher shop = voucher(1L, "SHOP10", Voucher.CreatorType.MERCHANT,
                Voucher.RewardType.FIXED, "10000", Voucher.ScopeType.SHOP, 7L, "0");
        Voucher platform = voucher(2L, "PLAT20", Voucher.CreatorType.PLATFORM,
                Voucher.RewardType.PERCENTAGE, "20", Voucher.ScopeType.ALL, null, "0");
        Voucher freeship = voucher(3L, "FREE15", Voucher.CreatorType.PLATFORM,
                Voucher.RewardType.FREESHIP, "15000", Voucher.ScopeType.ALL, null, "0");

        VoucherStackingCalculator.Calculation result = calculator.calculate(
                List.of(shop, platform, freeship), 7L,
                new BigDecimal("100000"), new BigDecimal("20000"),
                List.of(), VoucherSelectionMode.AUTO, NOW);

        assertThat(result.appliedVouchers()).extracting(VoucherStackingCalculator.AppliedVoucher::layer)
                .containsExactly(VoucherLayer.SHOP_DISCOUNT, VoucherLayer.PLATFORM_DISCOUNT, VoucherLayer.FREESHIP);
        assertThat(result.itemDiscount()).isEqualByComparingTo("28000");
        assertThat(result.shippingDiscount()).isEqualByComparingTo("15000");
        assertThat(result.totalDiscount()).isEqualByComparingTo("43000");
        assertThat(result.customerShippingFee()).isEqualByComparingTo("5000");
        assertThat(result.totalAmount()).isEqualByComparingTo("77000");
    }

    @Test
    void manualModeRejectsTwoVouchersFromTheSameLayer() {
        Voucher first = voucher(1L, "SHOP10", Voucher.CreatorType.MERCHANT,
                Voucher.RewardType.FIXED, "10000", Voucher.ScopeType.SHOP, 7L, "0");
        Voucher second = voucher(2L, "SHOP20", Voucher.CreatorType.MERCHANT,
                Voucher.RewardType.FIXED, "20000", Voucher.ScopeType.SHOP, 7L, "0");

        assertThatThrownBy(() -> calculator.calculate(
                List.of(first, second), 7L, new BigDecimal("100000"), BigDecimal.ZERO,
                List.of(1L, 2L), VoucherSelectionMode.MANUAL, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one voucher per layer");
    }

    @Test
    void shopAndFreeshipScopeAreCheckedServerSide() {
        Voucher wrongShop = voucher(1L, "OTHER", Voucher.CreatorType.MERCHANT,
                Voucher.RewardType.FIXED, "10000", Voucher.ScopeType.SHOP, 8L, "0");
        Voucher shopFreeship = voucher(2L, "SHOPFREE", Voucher.CreatorType.PLATFORM,
                Voucher.RewardType.FREESHIP, "10000", Voucher.ScopeType.SHOP, 7L, "0");

        VoucherStackingCalculator.Calculation result = calculator.calculate(
                List.of(wrongShop, shopFreeship), 7L,
                new BigDecimal("100000"), new BigDecimal("20000"),
                List.of(), VoucherSelectionMode.AUTO, NOW);

        assertThat(result.appliedVouchers()).isEmpty();
        assertThat(result.unavailableVouchers()).extracting(VoucherStackingCalculator.UnavailableVoucher::reason)
                .containsExactly("Not applicable for this shop", "Freeship voucher must be platform-wide");
    }

    private Voucher voucher(Long id, String code, Voucher.CreatorType creator, Voucher.RewardType reward,
                            String value, Voucher.ScopeType scope, Long scopeRef, String minOrder) {
        return Voucher.builder().id(id).code(code).name(code).creatorType(creator).rewardType(reward)
                .discountValue(new BigDecimal(value)).scopeType(scope).scopeRefId(scopeRef)
                .totalQuantity(100).usedQuantity(0).usageLimitPerUser(1)
                .startTime(NOW.minusDays(1)).endTime(NOW.plusDays(1))
                .minOrderValue(new BigDecimal(minOrder)).active(true).build();
    }
}
