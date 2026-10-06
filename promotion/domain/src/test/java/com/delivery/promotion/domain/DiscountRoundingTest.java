package com.delivery.promotion.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.*;

class DiscountRoundingTest {
    static Stream<Arguments> prices() {
        return Stream.of(
                Arguments.of(Voucher.RewardType.FIXED, "1.005", "10", "3", null, "1.01", "1.01"),
                Arguments.of(Voucher.RewardType.FIXED, "20", "10.004", "3", null, "10.00", null),
                Arguments.of(Voucher.RewardType.PERCENTAGE, "33.335", "10", "3", null, "3.33", "3.33"),
                Arguments.of(Voucher.RewardType.PERCENTAGE, "50", "10.005", "3", null, "5.00", "5.00"),
                Arguments.of(Voucher.RewardType.PERCENTAGE, "50", "10.01", "3", null, "5.01", "5.01"),
                Arguments.of(Voucher.RewardType.PERCENTAGE, "150", "10.004", "3", null, "10.00", null),
                Arguments.of(Voucher.RewardType.PERCENTAGE, "33.35", "10", "3", "3.334", "3.33", "3.33"),
                Arguments.of(Voucher.RewardType.PERCENTAGE, "33.35", "10", "3", "3.335", "3.34", "3.34"),
                Arguments.of(Voucher.RewardType.FIXED, "20", "10", "3", "2.005", "2.01", "2.01"),
                Arguments.of(Voucher.RewardType.FREESHIP, "20", "10", "3.005", null, "3.01", "3.01"),
                Arguments.of(Voucher.RewardType.FREESHIP, "2.005", "10", "3", null, "2.01", "2.01"),
                Arguments.of(Voucher.RewardType.FREESHIP, "20", "10", "3", "1.005", "1.01", "1.01"),
                Arguments.of(Voucher.RewardType.FIXED, "0", "10", "3", null, "0.00", "0.00"),
                Arguments.of(Voucher.RewardType.PERCENTAGE, "100", "0", "3", null, "0.00", null),
                Arguments.of(Voucher.RewardType.FREESHIP, "20", "10", "0", null, "0.00", "0.00")
        );
    }

    @ParameterizedTest
    @MethodSource("prices")
    void bothPathsPreserveExactBigDecimalScaleRoundingAndCaps(Voucher.RewardType reward, String value,
                                                              String subtotal, String shipping, String cap,
                                                              String legacy, String stacked) {
        TestVoucher voucher = VoucherRulesTest.valid();
        voucher.setRewardType(reward);
        voucher.setDiscountValue(new BigDecimal(value));
        voucher.setMaxDiscountValue(cap == null ? null : new BigDecimal(cap));
        BigDecimal food = new BigDecimal(subtotal);
        BigDecimal fee = new BigDecimal(shipping);
        assertThat(LegacyDiscountCalculator.calculate(voucher, food, fee)).isEqualTo(new BigDecimal(legacy));
        if (stacked == null) {
            assertThatThrownBy(() -> new VoucherStackingCalculator().calculate(List.of(voucher), 7L, food, fee,
                    List.of(1L), VoucherSelectionMode.MANUAL, LocalDateTime.of(2026, 9, 30, 12, 0)))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Voucher combination leaves no positive payable food amount");
        } else {
            var result = new VoucherStackingCalculator().calculate(List.of(voucher), 7L, food, fee,
                    List.of(1L), VoucherSelectionMode.MANUAL, LocalDateTime.of(2026, 9, 30, 12, 0));
            assertThat(result.totalDiscount()).isEqualTo(new BigDecimal(stacked));
        }
    }
}
