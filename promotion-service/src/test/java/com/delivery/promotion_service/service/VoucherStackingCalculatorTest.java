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
        Voucher shop = voucher(1L, "SHOP10", Voucher.CreatorType.SHOP,
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
        Voucher first = voucher(1L, "SHOP10", Voucher.CreatorType.SHOP,
                Voucher.RewardType.FIXED, "10000", Voucher.ScopeType.SHOP, 7L, "0");
        Voucher second = voucher(2L, "SHOP20", Voucher.CreatorType.SHOP,
                Voucher.RewardType.FIXED, "20000", Voucher.ScopeType.SHOP, 7L, "0");

        assertThatThrownBy(() -> calculator.calculate(
                List.of(first, second), 7L, new BigDecimal("100000"), BigDecimal.ZERO,
                List.of(1L, 2L), VoucherSelectionMode.MANUAL, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one voucher per layer");
    }

    @Test
    void shopAndFreeshipScopeAreCheckedServerSide() {
        Voucher wrongShop = voucher(1L, "OTHER", Voucher.CreatorType.SHOP,
                Voucher.RewardType.FIXED, "10000", Voucher.ScopeType.SHOP, 8L, "0");
        Voucher platformForOtherShop = voucher(4L, "PLAT_OTHER", Voucher.CreatorType.PLATFORM,
                Voucher.RewardType.FIXED, "10000", Voucher.ScopeType.SHOP, 8L, "0");
        Voucher shopFreeship = voucher(2L, "SHOPFREE", Voucher.CreatorType.PLATFORM,
                Voucher.RewardType.FREESHIP, "10000", Voucher.ScopeType.SHOP, 7L, "0");

        VoucherStackingCalculator.Calculation result = calculator.calculate(
                List.of(wrongShop, platformForOtherShop, shopFreeship), 7L,
                new BigDecimal("100000"), new BigDecimal("20000"),
                List.of(), VoucherSelectionMode.AUTO, NOW);

        assertThat(result.appliedVouchers()).isEmpty();
        assertThat(result.unavailableVouchers()).extracting(VoucherStackingCalculator.UnavailableVoucher::reason)
                .containsExactly("Not applicable for this shop", "Not applicable for this shop",
                        "Freeship voucher must be platform-wide");
    }

    @Test
    void autoTieBreakUsesNumericStableVoucherId() {
        Voucher idTen = voucher(10L, "TEN", Voucher.CreatorType.PLATFORM,
                Voucher.RewardType.FIXED, "10000", Voucher.ScopeType.ALL, null, "0");
        Voucher idTwo = voucher(2L, "TWO", Voucher.CreatorType.PLATFORM,
                Voucher.RewardType.FIXED, "10000", Voucher.ScopeType.ALL, null, "0");

        VoucherStackingCalculator.Calculation result = calculator.calculate(
                List.of(idTen, idTwo), 7L, new BigDecimal("100000"), new BigDecimal("15000"),
                List.of(), VoucherSelectionMode.AUTO, NOW);

        assertThat(result.appliedVouchers()).extracting(VoucherStackingCalculator.AppliedVoucher::voucherId)
                .containsExactly(2L);
    }

    @Test
    void manualSelectionRejectsACombinationThatLeavesNoPayableFoodAmount() {
        Voucher fullDiscount = voucher(4L, "FULL", Voucher.CreatorType.PLATFORM,
                Voucher.RewardType.PERCENTAGE, "100", Voucher.ScopeType.ALL, null, "0");

        assertThatThrownBy(() -> calculator.calculate(
                List.of(fullDiscount), 7L, new BigDecimal("100000"), new BigDecimal("15000"),
                List.of(4L), VoucherSelectionMode.MANUAL, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive payable food");

        VoucherStackingCalculator.Calculation auto = calculator.calculate(
                List.of(fullDiscount), 7L, new BigDecimal("100000"), new BigDecimal("15000"),
                List.of(), VoucherSelectionMode.AUTO, NOW);
        assertThat(auto.appliedVouchers()).isEmpty();
    }

    @Test
    void malformedAndLegacyVoucherRowsAreQuarantinedFromAutoQuote() {
        Voucher invalidLayer = voucher(5L, "TYPO", Voucher.CreatorType.PLATFORM,
                Voucher.RewardType.FIXED, "10000", Voucher.ScopeType.ALL, null, "0");
        invalidLayer.setLayerCode("PLATFROM_DISCOUNT");
        Voucher merchant = voucher(6L, "LEGACY", Voucher.CreatorType.MERCHANT,
                Voucher.RewardType.FIXED, "10000", Voucher.ScopeType.SHOP, 7L, "0");

        VoucherStackingCalculator.Calculation result = calculator.calculate(
                List.of(invalidLayer, merchant), 7L, new BigDecimal("100000"), new BigDecimal("15000"),
                List.of(), VoucherSelectionMode.AUTO, NOW);

        assertThat(result.appliedVouchers()).isEmpty();
        assertThat(result.unavailableVouchers())
                .extracting(VoucherStackingCalculator.UnavailableVoucher::reason)
                .containsExactly("Voucher layer is invalid", "Legacy MERCHANT voucher is not checkout-eligible");
    }

    @Test
    void creatorScopeAndLayerMismatchesAreQuarantinedFromCheckout() {
        Voucher category = voucher(7L, "CATEGORY", Voucher.CreatorType.PLATFORM,
                Voucher.RewardType.FIXED, "10000", Voucher.ScopeType.CATEGORY, 7L, "0");
        Voucher platformShopLayer = voucher(8L, "PLATFORM_SHOP", Voucher.CreatorType.PLATFORM,
                Voucher.RewardType.FIXED, "10000", Voucher.ScopeType.SHOP, 7L, "0");
        platformShopLayer.setLayerCode(VoucherLayer.SHOP_DISCOUNT.name());
        Voucher shopPlatformLayer = voucher(9L, "SHOP_PLATFORM", Voucher.CreatorType.SHOP,
                Voucher.RewardType.FIXED, "10000", Voucher.ScopeType.SHOP, 7L, "0");
        shopPlatformLayer.setLayerCode(VoucherLayer.PLATFORM_DISCOUNT.name());

        VoucherStackingCalculator.Calculation result = calculator.calculate(
                List.of(category, platformShopLayer, shopPlatformLayer), 7L,
                new BigDecimal("100000"), new BigDecimal("15000"),
                List.of(), VoucherSelectionMode.AUTO, NOW);

        assertThat(result.appliedVouchers()).isEmpty();
        assertThat(result.unavailableVouchers())
                .extracting(VoucherStackingCalculator.UnavailableVoucher::reason)
                .containsExactly(
                        "Legacy CATEGORY voucher is not checkout-eligible",
                        "Platform voucher cannot use the SHOP_DISCOUNT layer",
                        "Shop voucher must use the SHOP_DISCOUNT layer and SHOP scope");
    }

    @Test
    void fundingSourceIsCanonicalForTheResolvedLayer() {
        Voucher platform = voucher(7L, "PLATFORM", Voucher.CreatorType.PLATFORM,
                Voucher.RewardType.FIXED, "10000", Voucher.ScopeType.ALL, null, "0");
        platform.setFundingSource("SHOP");

        VoucherStackingCalculator.Calculation result = calculator.calculate(
                List.of(platform), 7L, new BigDecimal("100000"), new BigDecimal("15000"),
                List.of(7L), VoucherSelectionMode.MANUAL, NOW);

        assertThat(result.appliedVouchers()).singleElement()
                .satisfies(applied -> assertThat(applied.fundingSource()).isEqualTo("PLATFORM"));
    }

    @Test
    void negativeMaxDiscountRowsAreQuarantined() {
        Voucher malformed = voucher(10L, "NEG_CAP", Voucher.CreatorType.PLATFORM,
                Voucher.RewardType.PERCENTAGE, "20", Voucher.ScopeType.ALL, null, "0");
        malformed.setMaxDiscountValue(new BigDecimal("-1"));

        VoucherStackingCalculator.Calculation result = calculator.calculate(
                List.of(malformed), 7L, new BigDecimal("100000"), new BigDecimal("15000"),
                List.of(), VoucherSelectionMode.AUTO, NOW);

        assertThat(result.appliedVouchers()).isEmpty();
        assertThat(result.unavailableVouchers()).extracting(VoucherStackingCalculator.UnavailableVoucher::reason)
                .containsExactly("Voucher max discount is invalid");
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
