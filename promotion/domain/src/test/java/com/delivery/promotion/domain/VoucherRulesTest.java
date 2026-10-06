package com.delivery.promotion.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.*;

class VoucherRulesTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 30, 12, 0);
    private final VoucherStackingCalculator calculator = new VoucherStackingCalculator();

    static TestVoucher valid() {
        return TestVoucher.builder().id(1L).code("CODE").creatorType(Voucher.CreatorType.PLATFORM)
                .rewardType(Voucher.RewardType.FIXED).scopeType(Voucher.ScopeType.ALL)
                .discountValue(BigDecimal.ONE).totalQuantity(10).usedQuantity(0).active(true)
                .startTime(NOW.minusDays(1)).endTime(NOW.plusDays(1)).build();
    }

    static Stream<Arguments> malformed() {
        return Stream.of(
                mutation(voucher -> voucher.setDeletedAt(NOW), "Voucher is retired"),
                mutation(voucher -> voucher.setCreatorType(null), "Voucher ownership is invalid"),
                mutation(voucher -> voucher.setCreatorType(Voucher.CreatorType.MERCHANT), "Legacy MERCHANT voucher is not checkout-eligible"),
                mutation(voucher -> voucher.setRewardType(null), "Voucher reward type is invalid"),
                mutation(voucher -> voucher.setScopeType(null), "Voucher scope is invalid"),
                mutation(voucher -> voucher.setScopeRefId(7L), "Voucher scope identity is invalid"),
                mutation(voucher -> voucher.setScopeType(Voucher.ScopeType.SHOP), "Voucher scope identity is invalid"),
                mutation(voucher -> voucher.setScopeType(Voucher.ScopeType.CATEGORY), "Legacy CATEGORY voucher is not checkout-eligible"),
                mutation(voucher -> voucher.setEndTime(null), "Voucher expiration is invalid"),
                mutation(voucher -> voucher.setDiscountValue(null), "Voucher discount is invalid"),
                mutation(voucher -> voucher.setDiscountValue(new BigDecimal("-1")), "Voucher discount is invalid"),
                mutation(voucher -> voucher.setMinOrderValue(new BigDecimal("-1")), "Voucher minimum order is invalid"),
                mutation(voucher -> voucher.setMaxDiscountValue(new BigDecimal("-1")), "Voucher max discount is invalid"),
                mutation(voucher -> voucher.setTotalQuantity(null), "Voucher capacity is invalid"),
                mutation(voucher -> voucher.setTotalQuantity(0), "Voucher capacity is invalid"),
                mutation(voucher -> voucher.setUsedQuantity(null), "Voucher capacity is invalid"),
                mutation(voucher -> voucher.setUsedQuantity(-1), "Voucher capacity is invalid"),
                mutation(voucher -> voucher.setLayerCode("TYPO"), "Voucher layer is invalid"),
                mutation(voucher -> voucher.setLayerCode("SHOP_DISCOUNT"), "Platform voucher cannot use the SHOP_DISCOUNT layer"),
                mutation(voucher -> voucher.setCreatorType(Voucher.CreatorType.SHOP), "Shop voucher must use the SHOP_DISCOUNT layer and SHOP scope"),
                mutation(voucher -> voucher.setLayerCode("FREESHIP"), "Freeship layer requires a freeship reward"),
                mutation(voucher -> { voucher.setRewardType(Voucher.RewardType.FREESHIP); voucher.setLayerCode("PLATFORM_DISCOUNT"); }, "Freeship reward cannot be stacked as an item discount"),
                mutation(voucher -> { voucher.setRewardType(Voucher.RewardType.FREESHIP); voucher.setScopeType(Voucher.ScopeType.SHOP); voucher.setScopeRefId(7L); }, "Freeship voucher must be platform-wide")
        );
    }

    private static Arguments mutation(Consumer<TestVoucher> mutation, String reason) {
        return Arguments.of(mutation, reason);
    }

    @ParameterizedTest
    @MethodSource("malformed")
    void malformedRowsKeepTheirDistinctWalletAndStackingMessages(Consumer<TestVoucher> mutation, String reason) {
        TestVoucher voucher = valid();
        mutation.accept(voucher);
        assertThat(WalletVoucherPolicy.isCheckoutEligible(voucher)).isFalse();
        assertThat(WalletVoucherPolicy.reservationUnavailableReason(voucher, 7L, BigDecimal.TEN, NOW))
                .isEqualTo("Voucher is not checkout-eligible");
        assertThatThrownBy(() -> WalletVoucherPolicy.requireCollectable(voucher, NOW))
                .isExactlyInstanceOf(IllegalArgumentException.class).hasMessage("Voucher is not checkout-eligible");
        assertThat(quote(List.of(voucher)).unavailableVouchers()).extracting(VoucherStackingCalculator.UnavailableVoucher::reason)
                .containsExactly(reason);
    }

    static Stream<Arguments> unavailable() {
        return Stream.of(
                Arguments.of((Consumer<TestVoucher>) voucher -> voucher.setApprovalStatus("PENDING"), "Voucher is not approved", "Voucher is expired or inactive"),
                Arguments.of((Consumer<TestVoucher>) voucher -> voucher.setActive(false), "Voucher is inactive", "Voucher is expired or inactive"),
                Arguments.of((Consumer<TestVoucher>) voucher -> voucher.setActive(null), "Voucher is inactive", "Voucher is expired or inactive"),
                Arguments.of((Consumer<TestVoucher>) voucher -> voucher.setStartTime(NOW.plusSeconds(1)), "Voucher is not active yet", "Voucher is not active yet"),
                Arguments.of((Consumer<TestVoucher>) voucher -> voucher.setEndTime(NOW.minusSeconds(1)), "Voucher expired", "Voucher is expired or inactive"),
                Arguments.of((Consumer<TestVoucher>) voucher -> voucher.setUsedQuantity(10), "Out of stock", "Voucher is out of stock")
        );
    }

    @ParameterizedTest
    @MethodSource("unavailable")
    void availabilityPreservesValidationOrderAndExceptionTypes(Consumer<TestVoucher> mutation, String reason, String collectReason) {
        TestVoucher voucher = valid();
        mutation.accept(voucher);
        assertThat(WalletVoucherPolicy.isCheckoutEligible(voucher)).isTrue();
        assertThat(WalletVoucherPolicy.reservationUnavailableReason(voucher, 7L, BigDecimal.TEN, NOW)).isEqualTo(reason);
        assertThat(quote(List.of(voucher)).unavailableVouchers()).extracting(VoucherStackingCalculator.UnavailableVoucher::reason).containsExactly(reason);
        assertThatThrownBy(() -> WalletVoucherPolicy.requireCollectable(voucher, NOW))
                .isExactlyInstanceOf(IllegalArgumentException.class).hasMessage(collectReason);
    }

    @Test
    void nullAndBoundaryInputsKeepTheirContract() {
        assertThat(WalletVoucherPolicy.isApproved(null)).isFalse();
        assertThat(WalletVoucherPolicy.isCheckoutEligible(null)).isFalse();
        assertThatThrownBy(() -> VoucherLayerResolver.resolve(null)).hasMessage("Voucher reward type is required");
        TestVoucher voucher = valid();
        voucher.setRewardType(null);
        TestVoucher missingReward = voucher;
        assertThatThrownBy(() -> VoucherLayerResolver.resolve(missingReward)).hasMessage("Voucher reward type is required");
        voucher = valid();
        voucher.setApprovalStatus("approved");
        voucher.setStartTime(null);
        voucher.setMinOrderValue(BigDecimal.ZERO);
        voucher.setMaxDiscountValue(BigDecimal.ZERO);
        voucher.setLayerCode(" platform_discount ");
        assertThat(WalletVoucherPolicy.isApproved(voucher)).isTrue();
        assertThat(WalletVoucherPolicy.isCheckoutEligible(voucher)).isTrue();
        assertThat(WalletVoucherPolicy.reservationUnavailableReason(voucher, 7L, BigDecimal.TEN, NOW)).isNull();
        assertThatCode(() -> WalletVoucherPolicy.requireCollectable(valid(), NOW)).doesNotThrowAnyException();
        voucher.setLayerCode(" ");
        assertThat(VoucherLayerResolver.resolve(voucher)).isEqualTo(VoucherLayer.PLATFORM_DISCOUNT);
        voucher.setId(null);
        assertThat(quote(List.of(voucher)).unavailableVouchers().get(0).reason()).isEqualTo("Voucher ID is invalid");
        voucher.setId(0L);
        assertThat(quote(List.of(voucher)).unavailableVouchers().get(0).reason()).isEqualTo("Voucher ID is invalid");
        assertThat(quote(null).totalAmount()).isEqualTo(new BigDecimal("13.00"));
        assertThat(quote(Arrays.asList(null, valid())).totalDiscount()).isEqualTo(new BigDecimal("1.00"));
    }

    @Test
    void shopAndFreeshipWalletShapesAndScopeChecks() {
        TestVoucher voucher = valid();
        voucher.setCreatorType(Voucher.CreatorType.SHOP);
        voucher.setScopeType(Voucher.ScopeType.SHOP);
        voucher.setScopeRefId(7L);
        assertThat(WalletVoucherPolicy.isCheckoutEligible(voucher)).isTrue();
        assertThat(WalletVoucherPolicy.reservationUnavailableReason(voucher, 7L, BigDecimal.TEN, NOW)).isNull();
        voucher.setLayerCode("PLATFORM_DISCOUNT");
        assertThat(WalletVoucherPolicy.isCheckoutEligible(voucher)).isFalse();
        voucher.setLayerCode("SHOP_DISCOUNT");
        voucher.setRewardType(Voucher.RewardType.FREESHIP);
        assertThat(WalletVoucherPolicy.isCheckoutEligible(voucher)).isFalse();
        voucher = valid();
        voucher.setRewardType(Voucher.RewardType.FREESHIP);
        assertThat(WalletVoucherPolicy.isCheckoutEligible(voucher)).isTrue();
        assertThat(WalletVoucherPolicy.reservationUnavailableReason(voucher, 7L, BigDecimal.TEN, NOW)).isNull();
        voucher = valid();
        voucher.setScopeType(Voucher.ScopeType.SHOP);
        voucher.setScopeRefId(8L);
        assertThat(quote(List.of(voucher)).unavailableVouchers().get(0).reason()).isEqualTo("Not applicable for this shop");
        voucher.setScopeRefId(7L);
        voucher.setMinOrderValue(new BigDecimal("10.01"));
        assertThat(quote(List.of(voucher)).unavailableVouchers().get(0).reason()).isEqualTo("Need 0.01 more to use");
        voucher.setMinOrderValue(BigDecimal.TEN);
        assertThat(quote(List.of(voucher)).unavailableVouchers()).isEmpty();
    }

    @Test
    void manualSelectionAndInvalidQuoteArgumentsKeepMessages() {
        TestVoucher first = valid();
        TestVoucher second = valid();
        second.setId(2L);
        assertThatThrownBy(() -> calculate(List.of(first, second), List.of(1L, 2L), VoucherSelectionMode.MANUAL))
                .hasMessage("Only one voucher per layer is allowed: PLATFORM_DISCOUNT");
        assertThatThrownBy(() -> calculate(List.of(first), List.of(9L), VoucherSelectionMode.MANUAL))
                .hasMessage("Voucher is not in the wallet: 9");
        assertThatThrownBy(() -> calculate(List.of(first), List.of(1L, 1L), VoucherSelectionMode.AUTO))
                .hasMessage("Duplicate voucher IDs are not allowed");
        assertThatThrownBy(() -> calculate(List.of(first), List.of(1L, 2L, 3L, 4L), VoucherSelectionMode.AUTO))
                .hasMessage("At most one voucher per layer is supported");
        assertThat(calculate(List.of(first), Arrays.asList(null, 1L), VoucherSelectionMode.MANUAL).appliedVouchers()).hasSize(1);
        assertThat(calculate(List.of(first), null, null).appliedVouchers()).hasSize(1);
        assertThat(calculate(List.of(first), List.of(), VoucherSelectionMode.MANUAL).appliedVouchers()).isEmpty();
        for (BigDecimal invalid : Arrays.asList(null, new BigDecimal("-1"))) {
            assertThatThrownBy(() -> calculator.calculate(List.of(), 7L, invalid, BigDecimal.ONE, null, null, NOW)).hasMessage("subtotal must be non-negative");
            assertThatThrownBy(() -> calculator.calculate(List.of(), 7L, BigDecimal.ONE, invalid, null, null, NOW)).hasMessage("shippingFee must be non-negative");
        }
        for (Long invalid : Arrays.asList(null, 0L, -1L)) {
            assertThatThrownBy(() -> calculator.calculate(List.of(), invalid, BigDecimal.ONE, BigDecimal.ONE, null, null, NOW)).hasMessage("restaurantId must be positive");
        }
        assertThatThrownBy(() -> calculator.calculate(List.of(), 7L, BigDecimal.ONE, BigDecimal.ONE, null, null, null)).isExactlyInstanceOf(NullPointerException.class).hasMessage("now");
        assertThatThrownBy(() -> calculator.calculate(List.of(), 7L, BigDecimal.ZERO, BigDecimal.ZERO, null, null, NOW))
                .hasMessage("Voucher combination leaves no positive payable food amount");
    }

    @Test
    void tiesPreferEarlierExpirationThenLowerIdsRegardlessOfInputOrder() {
        TestVoucher high = valid();
        high.setId(9L);
        TestVoucher low = valid();
        low.setId(2L);
        assertThat(quote(List.of(high, low)).appliedVouchers().get(0).voucherId()).isEqualTo(2L);
        high.setEndTime(NOW.plusHours(1));
        assertThat(quote(List.of(low, high)).appliedVouchers().get(0).voucherId()).isEqualTo(9L);
        assertThat(quote(List.of(high, low)).appliedVouchers().get(0).voucherId()).isEqualTo(9L);
    }

    @Test
    void snapshotAccessorsAndCalculationListsAreImmutable() {
        TestVoucher voucher = valid();
        VoucherSnapshot snapshot = new VoucherSnapshot(voucher.getId(), voucher.getCode(), voucher.getCreatorType(),
                voucher.getRewardType(), voucher.getScopeType(), voucher.getScopeRefId(), voucher.getDiscountValue(),
                voucher.getMaxDiscountValue(), voucher.getMinOrderValue(), voucher.getTotalQuantity(), voucher.getUsedQuantity(),
                voucher.getActive(), voucher.getStartTime(), voucher.getEndTime(), voucher.getDeletedAt(), voucher.getLayerCode(), voucher.getApprovalStatus());
        assertThat(WalletVoucherPolicy.isCheckoutEligible(snapshot)).isTrue();
        assertThat(WalletVoucherPolicy.isApproved(snapshot)).isTrue();
        var result = quote(List.of(snapshot));
        assertThat(result.appliedVouchers().get(0).code()).isEqualTo("CODE");
        assertThatThrownBy(() -> result.appliedVouchers().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.unavailableVouchers().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private VoucherStackingCalculator.Calculation quote(List<? extends Voucher> vouchers) {
        return calculate(vouchers, null, VoucherSelectionMode.AUTO);
    }

    private VoucherStackingCalculator.Calculation calculate(List<? extends Voucher> vouchers, List<Long> selected, VoucherSelectionMode mode) {
        return calculator.calculate(vouchers, 7L, BigDecimal.TEN, new BigDecimal("3"), selected, mode, NOW);
    }
}
