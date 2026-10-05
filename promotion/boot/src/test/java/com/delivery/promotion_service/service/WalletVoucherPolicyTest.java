package com.delivery.promotion_service.service;

import com.delivery.promotion_service.entity.Voucher;
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
        Voucher voucher = voucher();
        voucher.setEndTime(NOW);
        assertThat(WalletVoucherPolicy.reservationUnavailableReason(voucher, 9L, BigDecimal.TEN, NOW))
                .isEqualTo("Voucher expired");
        // Preserve the existing collection boundary during this extraction.
        assertThatCode(() -> WalletVoucherPolicy.requireCollectable(voucher, NOW)).doesNotThrowAnyException();
    }

    @Test
    void startBoundaryIsInclusiveAndFutureStartIsRejected() {
        Voucher voucher = voucher();
        voucher.setStartTime(NOW);
        assertThat(WalletVoucherPolicy.reservationUnavailableReason(voucher, 9L, BigDecimal.TEN, NOW)).isNull();
        voucher.setStartTime(NOW.plusSeconds(1));
        assertThatThrownBy(() -> WalletVoucherPolicy.requireCollectable(voucher, NOW))
                .hasMessage("Voucher is not active yet");
    }

    @Test
    void reservationChecksRemainingCapacityMinimumAndRestaurant() {
        Voucher voucher = voucher();
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
        Voucher voucher = voucher();
        voucher.setDeletedAt(NOW.minusHours(1));
        assertThat(WalletVoucherPolicy.isCheckoutEligible(voucher)).isFalse();
        assertThatThrownBy(() -> WalletVoucherPolicy.requireCollectable(voucher, NOW))
                .hasMessage("Voucher is not checkout-eligible");
        assertThat(WalletVoucherPolicy.reservationUnavailableReason(voucher, 9L, BigDecimal.TEN, NOW))
                .isEqualTo("Voucher is not checkout-eligible");
    }

    private Voucher voucher() {
        return Voucher.builder().id(11L).creatorType(Voucher.CreatorType.PLATFORM)
                .rewardType(Voucher.RewardType.FIXED).discountValue(BigDecimal.ONE)
                .scopeType(Voucher.ScopeType.SHOP).scopeRefId(9L).active(true)
                .startTime(NOW.minusDays(1)).endTime(NOW.plusDays(1))
                .totalQuantity(10).usedQuantity(0).build();
    }
}
