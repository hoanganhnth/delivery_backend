package com.delivery.analytics.application;
import com.delivery.analytics.applicationapi.*;
import com.delivery.analytics.domain.DashboardValues.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DashboardServiceTest {
    private final List<String> calls=new ArrayList<>();
    private final List<DashboardReadPort.Scope> scopes=new ArrayList<>();
    private final Totals totals=new Totals(3,2,1,1,new BigDecimal("11"));
    private int reads;
    private final DashboardService service=new DashboardService(new DashboardReadPort() {
        public Totals totals(Scope scope) {scopes.add(scope);calls.add("totals");return reads++%2==0?totals:null;}
        public List<SeriesRow> monthly(Scope scope,int year) {
            scopes.add(scope);calls.add("monthly:"+year);return List.of(new SeriesRow(1,2,BigDecimal.TEN));
        }
        public List<SeriesRow> yearly(Scope scope) {scopes.add(scope);calls.add("yearly");return List.of(new SeriesRow(2024,3,BigDecimal.ONE));}
        public List<TopRestaurant> topRestaurants(int limit) {
            calls.add("top:"+limit);return List.of(new TopRestaurant(7L,"Shop",2,BigDecimal.TEN));
        }
    },()->{calls.add("today");return LocalDate.of(2026,10,6);});
    @Test void adminRetainsReadOrderIndependentTotalsAndTopTen() {
        var dashboard=service.admin("quarter",2025);
        assertEquals(List.of("totals","monthly:2025","totals","top:10"),calls);
        assertTrue(scopes.stream().allMatch(DashboardReadPort.Scope::platform));
        assertTrue(dashboard.statuses().isEmpty());assertEquals(-1,dashboard.overview().processing());
        assertEquals(4,dashboard.series().size());assertEquals("Shop",dashboard.topRestaurants().get(0).restaurantName());
    }
    @Test void yearIgnoresRequestedYearAndRetainsReadOrder() {
        var dashboard=service.restaurant(9L,"year",2050);
        assertEquals(List.of("totals","yearly","totals"),calls);assertEquals("2024",dashboard.series().get(0).label());
        assertTrue(dashboard.topRestaurants().isEmpty());assertTrue(scopes.stream().noneMatch(DashboardReadPort.Scope::platform));
        assertTrue(scopes.stream().allMatch(s->s.restaurantId().equals(9L)));
    }
    @Test void nullYearUsesLocalClockAndRestaurantNullIsStillRestaurantScope() {
        var dashboard=service.restaurant(null,"quarter",null);
        assertEquals(List.of("today","totals","monthly:2026","totals"),calls);
        assertEquals(4,dashboard.series().size());assertTrue(scopes.stream().noneMatch(DashboardReadPort.Scope::platform));
        assertTrue(scopes.stream().allMatch(s->s.restaurantId()==null));
    }
    @Test void nullAndUnsupportedAndUnnormalizedPeriodsUseMonthlyFallback() {
        for(String period:new String[]{null,"unknown"," YEAR ","Quarter","month"}) {
            calls.clear();reads=0;
            assertEquals(12,service.restaurant(8L,period,2026).series().size());
            assertEquals(List.of("totals","monthly:2026","totals"),calls);
        }
    }
    @Test void storageFailurePropagates() {
        var error=new IllegalStateException("query failed");
        var failing=new DashboardService(new DashboardReadPort() {
            public Totals totals(Scope scope) {throw error;}
            public List<SeriesRow> monthly(Scope scope,int year) {throw new AssertionError();}
            public List<SeriesRow> yearly(Scope scope) {throw new AssertionError();}
            public List<TopRestaurant> topRestaurants(int limit) {throw new AssertionError();}
        },LocalDate::now);
        assertSame(error,assertThrows(IllegalStateException.class,()->failing.admin("year",2026)));
    }
}
