package com.delivery.analytics.applicationapi;
import com.delivery.analytics.domain.DashboardValues.*;
import java.util.List;
public interface DashboardReadPort {
    record Scope(boolean platform, Long restaurantId) {}
    Totals totals(Scope scope);
    List<SeriesRow> monthly(Scope scope, int year);
    List<SeriesRow> yearly(Scope scope);
    List<TopRestaurant> topRestaurants(int limit);
}
