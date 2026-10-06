package com.delivery.analytics.domain;
import com.delivery.analytics.domain.DashboardValues.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DashboardValuesTest {
    @Test void overviewEmptyRoundingAndUnclampedProcessing() {
        assertEquals(new Overview(0,0,0,0,0,BigDecimal.ZERO,BigDecimal.ZERO,0),DashboardValues.overview(null));
        var overview=DashboardValues.overview(new Totals(3,2,4,1,new BigDecimal("11")));
        assertEquals(-4,overview.processing());assertEquals(new BigDecimal("6"),overview.average());assertEquals(66.7,overview.deliveryRate());
        assertEquals(BigDecimal.ZERO,DashboardValues.overview(new Totals(0,0,0,0,BigDecimal.TEN)).average());
        assertEquals(0,DashboardValues.overview(new Totals(-1,-1,0,0,BigDecimal.TEN)).deliveryRate());
    }
    @Test void allSeriesLabelsGapsOrderAndRevenue() {
        var rows=List.of(new SeriesRow(12,5,BigDecimal.TEN),new SeriesRow(1,2,BigDecimal.ONE),new SeriesRow(3,3,BigDecimal.TEN));
        var months=DashboardValues.monthly(rows);
        for(int m=1;m<=12;m++) assertEquals("T"+m,months.get(m-1).label());
        assertEquals(0,months.get(1).orderCount());assertEquals(BigDecimal.ZERO,months.get(1).revenue());
        var quarters=DashboardValues.quarterly(rows);
        assertEquals(List.of("Q1","Q2","Q3","Q4"),quarters.stream().map(Point::label).toList());
        assertEquals(5,quarters.get(0).orderCount());assertEquals(new BigDecimal("11"),quarters.get(0).revenue());
        assertEquals(5,quarters.get(3).orderCount());
        var years=DashboardValues.yearly(List.of(new SeriesRow(2026,2,BigDecimal.TEN),new SeriesRow(2024,3,BigDecimal.ONE)));
        assertEquals(List.of("2026","2024"),years.stream().map(Point::label).toList());assertEquals(2,years.get(0).orderCount());
        assertEquals(BigDecimal.TEN,years.get(0).revenue());assertTrue(DashboardValues.yearly(List.of()).isEmpty());
        assertEquals(0,DashboardValues.monthly(List.of(new SeriesRow(13,9,BigDecimal.TEN))).get(0).orderCount());
        assertThrows(IllegalStateException.class,()->DashboardValues.monthly(List.of(rows.get(0),rows.get(0))));
    }
    @Test void exactStatusOrderingIncludingZeros() {
        assertTrue(DashboardValues.statuses(null).isEmpty());
        assertEquals(List.of(new Status("DELIVERED",2),new Status("CANCELLED",3),new Status("PENDING",4)),
                DashboardValues.statuses(new Totals(10,2,3,4,BigDecimal.TEN)));
        var top=new TopRestaurant(7L,"Shop",3,BigDecimal.TEN);
        assertEquals(7L,top.restaurantId());assertEquals("Shop",top.restaurantName());assertEquals(3,top.orderCount());assertEquals(BigDecimal.TEN,top.revenue());
    }
}
