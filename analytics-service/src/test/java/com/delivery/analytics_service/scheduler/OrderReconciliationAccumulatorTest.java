package com.delivery.analytics_service.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OrderReconciliationAccumulatorTest {
    @Test
    void emptyReductionHasZeroCountsAndAverage() {
        assertThat(new OrderReconciliationAccumulator().snapshot()).isEqualTo(
                new OrderReconciliationAccumulator.Snapshot(0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void snapshotsAreImmutableAndPendingIsClampedWithoutDiscardingTerminalEvents() {
        var accumulator = new OrderReconciliationAccumulator();
        accumulator.accept("ORDER_CREATED", BigDecimal.TEN);
        var first = accumulator.snapshot();
        accumulator.accept("ORDER_DELIVERED", new BigDecimal("11"));
        accumulator.accept("ORDER_DELIVERED", null);
        accumulator.accept("ORDER_CANCELLED", BigDecimal.TEN);
        var last = accumulator.snapshot();
        assertThat(first.created()).isEqualTo(1);
        assertThat(first.pending()).isEqualTo(1);
        assertThat(first.revenue()).isZero();
        assertThat(last.created()).isEqualTo(1);
        assertThat(last.delivered()).isEqualTo(2);
        assertThat(last.cancelled()).isEqualTo(1);
        assertThat(last.pending()).isZero();
        assertThat(last.revenue()).isEqualByComparingTo("11");
        assertThat(last.averageOrderValue()).isEqualByComparingTo("6");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PAYMENT_COMPLETED", "PAYMENT_FAILED", "WITHDRAWAL_COMPLETED", "unknown"})
    void nonOrderEventsDoNotAffectSnapshot(String type) {
        var accumulator = new OrderReconciliationAccumulator();
        var before = accumulator.snapshot();
        accumulator.accept(type, BigDecimal.TEN);
        assertThat(accumulator.snapshot()).isEqualTo(before);
    }
}
