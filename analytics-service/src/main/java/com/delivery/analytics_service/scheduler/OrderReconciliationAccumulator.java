package com.delivery.analytics_service.scheduler;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure order-event reduction, independent of persistence and scheduling. */
final class OrderReconciliationAccumulator {
    private long created;
    private long delivered;
    private long cancelled;
    private BigDecimal revenue = BigDecimal.ZERO;

    void accept(String eventType, BigDecimal amount) {
        switch (eventType) {
            case "ORDER_CREATED" -> created++;
            case "ORDER_DELIVERED" -> {
                delivered++;
                if (amount != null) revenue = revenue.add(amount);
            }
            case "ORDER_CANCELLED" -> cancelled++;
            default -> {
                // Payment and other events do not contribute to order statistics.
            }
        }
    }

    Snapshot snapshot() {
        return new Snapshot(created, delivered, cancelled,
                Math.max(0, created - delivered - cancelled), revenue,
                delivered > 0
                        ? revenue.divide(BigDecimal.valueOf(delivered), 0, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO);
    }

    record Snapshot(long created, long delivered, long cancelled, long pending,
                    BigDecimal revenue, BigDecimal averageOrderValue) { }
}
