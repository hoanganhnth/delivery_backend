package com.delivery.analytics.domain;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;
public final class DashboardValues {
    private DashboardValues() {}
    public record Totals(long total, long delivered, long cancelled, long pending, BigDecimal revenue) {}
    public record Overview(long total, long delivered, long cancelled, long pending, long processing,
                           BigDecimal revenue, BigDecimal average, double deliveryRate) {}
    public record SeriesRow(int period, long orders, BigDecimal revenue) {}
    public record Point(String label, long orderCount, BigDecimal revenue) {}
    public record Status(String status, long count) {}
    public record TopRestaurant(Long restaurantId, String restaurantName, long orderCount, BigDecimal revenue) {}
    public static Overview overview(Totals row) {
        if (row == null) return new Overview(0,0,0,0,0,BigDecimal.ZERO,BigDecimal.ZERO,0);
        BigDecimal avg = row.delivered > 0
                ? row.revenue.divide(BigDecimal.valueOf(row.delivered), 0, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        double rate = row.total > 0 ? Math.round((double) row.delivered / row.total * 1000.0) / 10.0 : 0;
        return new Overview(row.total,row.delivered,row.cancelled,row.pending,
                row.total-row.delivered-row.cancelled-row.pending,row.revenue,avg,rate);
    }
    public static List<Point> monthly(List<SeriesRow> rows) {
        Map<Integer, SeriesRow> map = rows.stream().collect(Collectors.toMap(SeriesRow::period, r -> r));
        List<Point> points = new ArrayList<>();
        for (int m=1;m<=12;m++) {
            SeriesRow row=map.get(m);
            points.add(new Point("T"+m,row != null ? row.orders : 0,row != null ? row.revenue : BigDecimal.ZERO));
        }
        return points;
    }
    public static List<Point> quarterly(List<SeriesRow> rows) {
        List<Point> months=monthly(rows), quarters=new ArrayList<>();
        for(int q=0;q<4;q++) {
            long orders=0; BigDecimal revenue=BigDecimal.ZERO;
            for(int m=q*3;m<(q+1)*3;m++) {
                orders+=months.get(m).orderCount; revenue=revenue.add(months.get(m).revenue);
            }
            quarters.add(new Point("Q"+(q+1),orders,revenue));
        }
        return quarters;
    }
    public static List<Point> yearly(List<SeriesRow> rows) {
        return rows.stream().map(r -> new Point(String.valueOf(r.period),r.orders,r.revenue)).collect(Collectors.toList());
    }
    public static List<Status> statuses(Totals row) {
        if(row==null) return List.of();
        return List.of(new Status("DELIVERED",row.delivered),new Status("CANCELLED",row.cancelled),new Status("PENDING",row.pending));
    }
}
