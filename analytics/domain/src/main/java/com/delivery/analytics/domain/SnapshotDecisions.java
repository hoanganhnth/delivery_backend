package com.delivery.analytics.domain;
import java.math.BigDecimal;
import java.math.RoundingMode;
/** Pure decisions on adapter-decoded values; JSON shape and timestamp syntax stay in the host. */
public final class SnapshotDecisions {
    private SnapshotDecisions() {}
    public static long positive(long value, String message) {
        if (value <= 0) throw new IllegalArgumentException(message);
        return value;
    }
    public static void requireSize(int size) {
        if (size > 100) throw new IllegalArgumentException("analytics item snapshot exceeds 100 lines");
    }
    public static BigDecimal price(BigDecimal value) {
        if (value.signum() <= 0 || value.scale() > 2)
            throw new IllegalArgumentException("analytics item price is invalid");
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }
    public static Item item(long id, long quantity, BigDecimal price, BigDecimal total, String name) {
        BigDecimal expected = price.multiply(BigDecimal.valueOf(quantity));
        if (total == null) total = expected;
        if (total.compareTo(expected) != 0)
            throw new IllegalArgumentException("analytics item line total does not reconcile");
        String trimmed = name.trim();
        return new Item(id, quantity, total, trimmed.isBlank() ? "UNKNOWN" : trimmed);
    }
    public static String firstTimestamp(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }
    public record Item(long menuItemId, long quantity, BigDecimal lineTotal, String menuItemName) {
        public Delta delta(boolean cancelled) {
            return new Delta(cancelled ? 0 : quantity, cancelled ? quantity : 0,
                    cancelled ? BigDecimal.ZERO : lineTotal, cancelled ? lineTotal : BigDecimal.ZERO);
        }
    }
    public record Delta(long orderedQuantity, long cancelledQuantity,
                        BigDecimal orderedRevenue, BigDecimal cancelledRevenue) {}
}
