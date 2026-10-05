package com.delivery.promotion.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletVoucherPolicyTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 30, 12, 0);

    @Test
    void reservationExpiresAtTheExactEndBoundary() {
        TestVoucher voucher = voucher();
        voucher.setEndTime(NOW);
        assertThat(WalletVoucherPolicy.reservationUnavailableReason(voucher, 9L, BigDecimal.TEN, NOW))
                .isEqualTo("Voucher expired");
        assertThatCode(() -> WalletVoucherPolicy.requireCollectable(voucher, NOW)).doesNotThrowAnyException();
    }

    @Test
    void startBoundaryIsInclusiveAndFutureStartIsRejected() {
        TestVoucher voucher = voucher();
        voucher.setStartTime(NOW);
        assertThat(WalletVoucherPolicy.reservationUnavailableReason(voucher, 9L, BigDecimal.TEN, NOW)).isNull();
        voucher.setStartTime(NOW.plusSeconds(1));
        assertThatThrownBy(() -> WalletVoucherPolicy.requireCollectable(voucher, NOW))
                .hasMessage("Voucher is not active yet");
    }

    @Test
    void reservationChecksRemainingCapacityMinimumAndRestaurant() {
        TestVoucher voucher = voucher();
        voucher.setUsedQuantity(10);
        assertThat(WalletVoucherPolicy.reservationUnavailableReason(voucher, 9L, BigDecimal.TEN, NOW))
                .isEqualTo("Out of stock");
        voucher.setUsedQuantity(0);
        voucher.setMinOrderValue(new BigDecimal("20"));
        assertThat(WalletVoucherPolicy.reservationUnavailableReason(voucher, 9L, BigDecimal.TEN, NOW))
                .isEqualTo("Need 10 more to use");
        voucher.setMinOrderValue(BigDecimal.ZERO);
        assertThat(WalletVoucherPolicy.reservationUnavailableReason(voucher, 8L, BigDecimal.TEN, NOW))
                .isEqualTo("Not applicable for this shop");
    }

    @Test
    void deletedVoucherCannotBeCollectedOrReservedEvenWithActiveFlag() {
        TestVoucher voucher = voucher();
        voucher.setDeletedAt(NOW.minusHours(1));
        assertThat(WalletVoucherPolicy.isCheckoutEligible(voucher)).isFalse();
        assertThatThrownBy(() -> WalletVoucherPolicy.requireCollectable(voucher, NOW))
                .hasMessage("Voucher is not checkout-eligible");
        assertThat(WalletVoucherPolicy.reservationUnavailableReason(voucher, 9L, BigDecimal.TEN, NOW))
                .isEqualTo("Voucher is not checkout-eligible");
    }

    @Test
    void collectionOutcomeRetainsShapeApprovalTimeAndGlobalQuotaPrecedence() {
        var voucher = voucher();
        voucher.setUsedQuantity(10); voucher.setStartTime(NOW.plusSeconds(1));
        voucher.setActive(false); voucher.setDeletedAt(NOW);
        assertThat(WalletVoucherPolicy.collectionUnavailableReason(voucher, NOW)).isEqualTo("Voucher is not checkout-eligible");
        voucher.setDeletedAt(null);
        assertThat(WalletVoucherPolicy.collectionUnavailableReason(voucher, NOW)).isEqualTo("Voucher is expired or inactive");
        voucher.setActive(true); voucher.setApprovalStatus("PENDING");
        assertThat(WalletVoucherPolicy.collectionUnavailableReason(voucher, NOW)).isEqualTo("Voucher is expired or inactive");
        voucher.setApprovalStatus("APPROVED"); voucher.setEndTime(NOW.minusSeconds(1));
        assertThat(WalletVoucherPolicy.collectionUnavailableReason(voucher, NOW)).isEqualTo("Voucher is expired or inactive");
        voucher.setEndTime(NOW);
        assertThat(WalletVoucherPolicy.collectionUnavailableReason(voucher, NOW)).isEqualTo("Voucher is not active yet");
        voucher.setStartTime(null);
        assertThat(WalletVoucherPolicy.collectionUnavailableReason(voucher, NOW)).isEqualTo("Voucher is out of stock");
        voucher.setUsedQuantity(9);
        assertThat(WalletVoucherPolicy.collectionUnavailableReason(voucher, NOW)).isNull();
    }

    private TestVoucher voucher() {
        return TestVoucher.builder().id(11L).creatorType(TestVoucher.CreatorType.PLATFORM)
                .rewardType(TestVoucher.RewardType.FIXED).discountValue(BigDecimal.ONE)
                .scopeType(TestVoucher.ScopeType.SHOP).scopeRefId(9L).active(true)
                .startTime(NOW.minusDays(1)).endTime(NOW.plusDays(1))
                .totalQuantity(10).usedQuantity(0).build();
    }
}
