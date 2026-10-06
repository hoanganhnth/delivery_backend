package com.delivery.analytics.domain;
import java.math.BigDecimal;
import java.math.RoundingMode;
/** Read/modify/save decisions matching the native atomic adapter operations. */
public record OrderProjection(long total, long delivered, long cancelled, long pending,
                              BigDecimal revenue, BigDecimal average) {
    public OrderProjection created() {
        return new OrderProjection(total + 1, delivered, cancelled, pending + 1, revenue, average);
    }
    public OrderProjection delivered(BigDecimal amount) {
        long count = delivered + 1;
        BigDecimal sum = revenue.add(amount != null ? amount : BigDecimal.ZERO);
        return new OrderProjection(total, count, cancelled, pending > 0 ? pending - 1 : pending,
                sum, count > 0 ? sum.divide(BigDecimal.valueOf(count), 0, RoundingMode.HALF_UP) : average);
    }
    public OrderProjection cancel() {
        return new OrderProjection(total, delivered, cancelled + 1, pending > 0 ? pending - 1 : pending, revenue, average);
    }
}
