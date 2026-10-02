package com.delivery.restaurant.application.api;

import java.util.List;
import java.util.Optional;

public interface RestaurantReadUseCase {
    Optional<RestaurantSnapshot> findById(Long id);

    List<RestaurantSnapshot> listPublic();

    List<RestaurantSnapshot> searchPublic(String keyword);

    RestaurantPageSlice pagePublic(int page, int size, String keyword);
}
