package com.delivery.restaurant_service.service;

import com.delivery.restaurant_service.dto.request.CreateRestaurantRequest;
import com.delivery.restaurant_service.dto.request.UpdateRestaurantRequest;
import com.delivery.restaurant_service.dto.response.RestaurantResponse;
import com.delivery.restaurant_service.entity.Restaurant;

import java.util.List;

public interface RestaurantService {

    RestaurantResponse createRestaurant(CreateRestaurantRequest restaurant,
                                        Long creatorId,
                                        String role);

    RestaurantResponse updateRestaurant(Long id,
                                        UpdateRestaurantRequest restaurant,
                                        Long creatorId);

    void deleteRestaurant(Long id, Long creatorId);

    RestaurantResponse getRestaurantById(Long id);

    List<RestaurantResponse> getAllRestaurants();

    List<RestaurantResponse> findByName(String keyword);
}
