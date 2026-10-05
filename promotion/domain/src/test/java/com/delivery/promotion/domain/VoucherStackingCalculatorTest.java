package com.delivery.promotion.domain;

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
    void deletedVoucherIsExcludedEvenWhenItsActiveFlagIsStillTrue() {
        TestVoucher deleted = voucher(11L, "RETIRED", TestVoucher.CreatorType.PLATFORM,
                TestVoucher.RewardType.FIXED, "10000", TestVoucher.ScopeType.ALL, null, "0");
        deleted.setDeletedAt(NOW.minusHours(1));

        var result = calculator.calculate(List.of(deleted), 7L, new BigDecimal("100000"),
                BigDecimal.ZERO, List.of(), VoucherSelectionMode.AUTO, NOW);

        assertThat(result.appliedVouchers()).isEmpty();
        assertThat(result.unavailableVouchers()).extracting(VoucherStackingCalculator.UnavailableVoucher::reason)
                .containsExactly("Voucher is retired");
        assertThatThrownBy(() -> calculator.calculate(List.of(deleted), 7L,
                new BigDecimal("100000"), BigDecimal.ZERO, List.of(11L), VoucherSelectionMode.MANUAL, NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unavailable");
    }

    @Test
    void autoModeChoosesBestThreeLayerCombinationAndAppliesInOrder() {
        TestVoucher shop = voucher(1L, "SHOP10", TestVoucher.CreatorType.SHOP,
                TestVoucher.RewardType.FIXED, "10000", TestVoucher.ScopeType.SHOP, 7L, "0");
        TestVoucher platform = voucher(2L, "PLAT20", TestVoucher.CreatorType.PLATFORM,
                TestVoucher.RewardType.PERCENTAGE, "20", TestVoucher.ScopeType.ALL, null, "0");
        TestVoucher freeship = voucher(3L, "FREE15", TestVoucher.CreatorType.PLATFORM,
                TestVoucher.RewardType.FREESHIP, "15000", TestVoucher.ScopeType.ALL, null, "0");

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
        TestVoucher first = voucher(1L, "SHOP10", TestVoucher.CreatorType.SHOP,
                TestVoucher.RewardType.FIXED, "10000", TestVoucher.ScopeType.SHOP, 7L, "0");
        TestVoucher second = voucher(2L, "SHOP20", TestVoucher.CreatorType.SHOP,
                TestVoucher.RewardType.FIXED, "20000", TestVoucher.ScopeType.SHOP, 7L, "0");

        assertThatThrownBy(() -> calculator.calculate(
                List.of(first, second), 7L, new BigDecimal("100000"), BigDecimal.ZERO,
                List.of(1L, 2L), VoucherSelectionMode.MANUAL, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one voucher per layer");
    }

    @Test
    void shopAndFreeshipScopeAreCheckedServerSide() {
        TestVoucher wrongShop = voucher(1L, "OTHER", TestVoucher.CreatorType.SHOP,
                TestVoucher.RewardType.FIXED, "10000", TestVoucher.ScopeType.SHOP, 8L, "0");
        TestVoucher platformForOtherShop = voucher(4L, "PLAT_OTHER", TestVoucher.CreatorType.PLATFORM,
                TestVoucher.RewardType.FIXED, "10000", TestVoucher.ScopeType.SHOP, 8L, "0");
        TestVoucher shopFreeship = voucher(2L, "SHOPFREE", TestVoucher.CreatorType.PLATFORM,
                TestVoucher.RewardType.FREESHIP, "10000", TestVoucher.ScopeType.SHOP, 7L, "0");

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
        TestVoucher idTen = voucher(10L, "TEN", TestVoucher.CreatorType.PLATFORM,
                TestVoucher.RewardType.FIXED, "10000", TestVoucher.ScopeType.ALL, null, "0");
        TestVoucher idTwo = voucher(2L, "TWO", TestVoucher.CreatorType.PLATFORM,
                TestVoucher.RewardType.FIXED, "10000", TestVoucher.ScopeType.ALL, null, "0");

        VoucherStackingCalculator.Calculation result = calculator.calculate(
                List.of(idTen, idTwo), 7L, new BigDecimal("100000"), new BigDecimal("15000"),
                List.of(), VoucherSelectionMode.AUTO, NOW);

        assertThat(result.appliedVouchers()).extracting(VoucherStackingCalculator.AppliedVoucher::voucherId)
                .containsExactly(2L);
    }

    @Test
    void manualSelectionRejectsACombinationThatLeavesNoPayableFoodAmount() {
        TestVoucher fullDiscount = voucher(4L, "FULL", TestVoucher.CreatorType.PLATFORM,
                TestVoucher.RewardType.PERCENTAGE, "100", TestVoucher.ScopeType.ALL, null, "0");

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
        TestVoucher invalidLayer = voucher(5L, "TYPO", TestVoucher.CreatorType.PLATFORM,
                TestVoucher.RewardType.FIXED, "10000", TestVoucher.ScopeType.ALL, null, "0");
        invalidLayer.setLayerCode("PLATFROM_DISCOUNT");
        TestVoucher merchant = voucher(6L, "LEGACY", TestVoucher.CreatorType.MERCHANT,
                TestVoucher.RewardType.FIXED, "10000", TestVoucher.ScopeType.SHOP, 7L, "0");

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
        TestVoucher category = voucher(7L, "CATEGORY", TestVoucher.CreatorType.PLATFORM,
                TestVoucher.RewardType.FIXED, "10000", TestVoucher.ScopeType.CATEGORY, 7L, "0");
        TestVoucher platformShopLayer = voucher(8L, "PLATFORM_SHOP", TestVoucher.CreatorType.PLATFORM,
                TestVoucher.RewardType.FIXED, "10000", TestVoucher.ScopeType.SHOP, 7L, "0");
        platformShopLayer.setLayerCode(VoucherLayer.SHOP_DISCOUNT.name());
        TestVoucher shopPlatformLayer = voucher(9L, "SHOP_PLATFORM", TestVoucher.CreatorType.SHOP,
                TestVoucher.RewardType.FIXED, "10000", TestVoucher.ScopeType.SHOP, 7L, "0");
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
        TestVoucher platform = voucher(7L, "PLATFORM", TestVoucher.CreatorType.PLATFORM,
                TestVoucher.RewardType.FIXED, "10000", TestVoucher.ScopeType.ALL, null, "0");
        platform.setFundingSource("SHOP");

        VoucherStackingCalculator.Calculation result = calculator.calculate(
                List.of(platform), 7L, new BigDecimal("100000"), new BigDecimal("15000"),
                List.of(7L), VoucherSelectionMode.MANUAL, NOW);

        assertThat(result.appliedVouchers()).singleElement()
                .satisfies(applied -> assertThat(applied.fundingSource()).isEqualTo("PLATFORM"));
    }

    @Test
    void negativeMaxDiscountRowsAreQuarantined() {
        TestVoucher malformed = voucher(10L, "NEG_CAP", TestVoucher.CreatorType.PLATFORM,
                TestVoucher.RewardType.PERCENTAGE, "20", TestVoucher.ScopeType.ALL, null, "0");
        malformed.setMaxDiscountValue(new BigDecimal("-1"));

        VoucherStackingCalculator.Calculation result = calculator.calculate(
                List.of(malformed), 7L, new BigDecimal("100000"), new BigDecimal("15000"),
                List.of(), VoucherSelectionMode.AUTO, NOW);

        assertThat(result.appliedVouchers()).isEmpty();
        assertThat(result.unavailableVouchers()).extracting(VoucherStackingCalculator.UnavailableVoucher::reason)
                .containsExactly("Voucher max discount is invalid");
    }

    private TestVoucher voucher(Long id, String code, TestVoucher.CreatorType creator, TestVoucher.RewardType reward,
                            String value, TestVoucher.ScopeType scope, Long scopeRef, String minOrder) {
        return TestVoucher.builder().id(id).code(code).name(code).creatorType(creator).rewardType(reward)
                .discountValue(new BigDecimal(value)).scopeType(scope).scopeRefId(scopeRef)
                .totalQuantity(100).usedQuantity(0).usageLimitPerUser(1)
                .startTime(NOW.minusDays(1)).endTime(NOW.plusDays(1))
                .minOrderValue(new BigDecimal(minOrder)).active(true).build();
    }
}
