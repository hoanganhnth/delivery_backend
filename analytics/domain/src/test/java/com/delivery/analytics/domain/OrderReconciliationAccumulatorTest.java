package com.delivery.analytics.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderReconciliationAccumulatorTest {
    @Test
    void emptyReductionHasZeroCountsAndAverage() {
        assertEquals(new OrderReconciliationAccumulator.Snapshot(
                0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO),
                new OrderReconciliationAccumulator().snapshot());
    }

    @Test
    void snapshotsAreImmutableAndPendingIsClampedWithoutDiscardingTerminalEvents() {
        var accumulator = new OrderReconciliationAccumulator();
        accumulator.accept("ORDER_CREATED", BigDecimal.TEN);
        var first = accumulator.snapshot();
        accumulator.accept("ORDER_DELIVERED", new BigDecimal("11"));
        accumulator.accept("ORDER_DELIVERED", null);
        accumulator.accept("ORDER_CANCELLED", BigDecimal.TEN);
        assertEquals(new OrderReconciliationAccumulator.Snapshot(
                1, 0, 0, 1, BigDecimal.ZERO, BigDecimal.ZERO), first);
        assertEquals(new OrderReconciliationAccumulator.Snapshot(
                1, 2, 1, 0, new BigDecimal("11"), new BigDecimal("6")), accumulator.snapshot());
        assertEquals(accumulator.snapshot(), accumulator.snapshot());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PAYMENT_COMPLETED", "PAYMENT_FAILED", "WITHDRAWAL_COMPLETED",
            "unknown", "", "order_created", "order_delivered", "order_cancelled", " ORDER_CREATED "})
    void nonOrderEventsDoNotAffectSnapshot(String type) {
        var accumulator = new OrderReconciliationAccumulator();
        accumulator.accept("ORDER_CREATED", null);
        var before = accumulator.snapshot();
        accumulator.accept(type, BigDecimal.TEN);
        accumulator.accept(type, null);
        assertEquals(before, accumulator.snapshot());
    }

    @ParameterizedTest
    @CsvSource({"0,0", "0.49,0", "0.50,1", "1.49,1", "1.50,2",
            "-0.49,0", "-0.50,-1", "-1.50,-2", "100000000000000000000.50,100000000000000000001"})
    void roundsDeliveredAverageToWholeUnitsHalfUpWithoutRejectingNegativeAmounts(
            String revenue, String average) {
        var accumulator = new OrderReconciliationAccumulator();
        accumulator.accept("ORDER_DELIVERED", new BigDecimal(revenue));
        var result = accumulator.snapshot();
        assertEquals(1, result.delivered());
        assertEquals(0, result.pending());
        assertEquals(new BigDecimal(revenue), result.revenue());
        assertEquals(new BigDecimal(average), result.averageOrderValue());
    }

    @Test
    void nullDeliveredAmountStillCountsInTheAverageDenominator() {
        var accumulator = new OrderReconciliationAccumulator();
        accumulator.accept("ORDER_DELIVERED", null);
        assertEquals(new OrderReconciliationAccumulator.Snapshot(
                0, 1, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO), accumulator.snapshot());
        accumulator.accept("ORDER_DELIVERED", new BigDecimal("10.00"));
        assertEquals(new BigDecimal("10.00"), accumulator.snapshot().revenue());
        assertEquals(new BigDecimal("5"), accumulator.snapshot().averageOrderValue());
    }

    @Test
    void createdAndCancelledAmountsNeverContributeRevenue() {
        var accumulator = new OrderReconciliationAccumulator();
        accumulator.accept("ORDER_CREATED", new BigDecimal("-17.23"));
        accumulator.accept("ORDER_CREATED", null);
        accumulator.accept("ORDER_CREATED", BigDecimal.TEN);
        accumulator.accept("ORDER_CANCELLED", new BigDecimal("99.99"));
        accumulator.accept("ORDER_CANCELLED", null);
        assertEquals(new OrderReconciliationAccumulator.Snapshot(
                3, 0, 2, 1, BigDecimal.ZERO, BigDecimal.ZERO), accumulator.snapshot());
    }

    @Test
    void terminalBeforeCreatedAndCreatedBeforeTerminalHaveTheSameReduction() {
        var forward = new OrderReconciliationAccumulator();
        var reverse = new OrderReconciliationAccumulator();
        var types = List.of("ORDER_CREATED", "ORDER_CREATED", "ORDER_CREATED",
                "ORDER_DELIVERED", "ORDER_CANCELLED", "PAYMENT_COMPLETED");
        for (var type : types) forward.accept(type, new BigDecimal("10.25"));
        for (int i = types.size() - 1; i >= 0; i--) reverse.accept(types.get(i), new BigDecimal("10.25"));
        assertEquals(forward.snapshot(), reverse.snapshot());
        assertEquals(1, reverse.snapshot().pending());
    }

    @Test
    void nullEventTypeRetainsNullPointerExceptionAndDoesNotMutateCounts() {
        var accumulator = new OrderReconciliationAccumulator();
        accumulator.accept("ORDER_CREATED", null);
        var before = accumulator.snapshot();
        assertThrows(NullPointerException.class, () -> accumulator.accept(null, BigDecimal.TEN));
        assertEquals(before, accumulator.snapshot());
    }
}
