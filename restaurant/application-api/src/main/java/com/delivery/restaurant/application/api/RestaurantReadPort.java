package com.delivery.restaurant.application.api;

import java.util.List;
import java.util.Optional;

/** Persistence query boundary for public and management Restaurant reads. */
public interface RestaurantReadPort {
    Optional<RestaurantSnapshot> findById(Long id);

    List<RestaurantSnapshot> findPublic(int limit);

    List<RestaurantSnapshot> searchPublic(String keyword, int limit);

    RestaurantPageSlice pagePublic(int page, int size, String keyword);

    RestaurantManagementResult findManagement(RestaurantManagementQuery query);
}
