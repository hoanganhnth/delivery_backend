package com.delivery.restaurant_service.service;

import com.delivery.restaurant_service.entity.RestaurantBalance;

import java.util.List;

public interface  RestaurantBalanceService {

    // crud
    List<RestaurantBalance> getAllBalancesByRestaurantId(Long restaurantId);
    RestaurantBalance getRestaurantBalanceById(Long id);
    RestaurantBalance createRestaurantBalance(RestaurantBalance restaurantBalance);
    RestaurantBalance updateRestaurantBalance(Long id, RestaurantBalance restaurantBalance);
    void deleteRestaurantBalance(Long id);
}
