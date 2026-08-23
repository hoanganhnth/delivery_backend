package com.delivery.restaurant_service.service;

import com.delivery.restaurant_service.dto.request.CreateRestaurantRequest;
import com.delivery.restaurant_service.dto.request.UpdateRestaurantRequest;
import com.delivery.restaurant_service.dto.response.RestaurantResponse;

import java.util.List;
import org.springframework.data.domain.Page;

public interface RestaurantService {

    RestaurantResponse createRestaurant(CreateRestaurantRequest restaurant,
                                        Long creatorId,
                                        String role);
    RestaurantResponse createRestaurant(CreateRestaurantRequest restaurant,
                                        Long ownerPrincipalId,
                                        Long legacyCreatorId,
                                        String role);

    RestaurantResponse updateRestaurant(Long id,
                                        UpdateRestaurantRequest restaurant,
                                        Long creatorId,
                                        String role);
    RestaurantResponse updateRestaurant(Long id, UpdateRestaurantRequest restaurant,
                                        Long ownerPrincipalId, Long legacyCreatorId, String role);

    void deleteRestaurant(Long id, Long creatorId, String role);
    void deleteRestaurant(Long id, Long ownerPrincipalId, Long legacyCreatorId, String role);

    RestaurantResponse getRestaurantById(Long id);

    List<RestaurantResponse> getAllRestaurants();

    List<RestaurantResponse> findByName(String keyword);
    
    List<RestaurantResponse> getRestaurantsByCreatorId(Long creatorId);
    List<RestaurantResponse> getRestaurantsByOwnerPrincipalId(Long ownerPrincipalId, Long legacyCreatorId);
    Page<RestaurantResponse> getAllRestaurantsPage(int page, int size, String keyword);
}
