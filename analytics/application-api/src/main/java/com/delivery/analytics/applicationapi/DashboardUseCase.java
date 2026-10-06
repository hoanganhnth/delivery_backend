package com.delivery.analytics.applicationapi;
import com.delivery.analytics.domain.DashboardValues.*;
import java.util.List;
public interface DashboardUseCase {
    Dashboard admin(String period, Integer year);
    Dashboard restaurant(Long restaurantId, String period, Integer year);
    record Dashboard(Overview overview, List<Point> series, List<Status> statuses, List<TopRestaurant> topRestaurants) {}
}
