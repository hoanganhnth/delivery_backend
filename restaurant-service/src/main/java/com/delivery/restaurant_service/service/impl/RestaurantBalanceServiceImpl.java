package com.delivery.restaurant_service.service.impl;

import com.delivery.restaurant_service.entity.RestaurantBalance;
import com.delivery.restaurant_service.mapper.RestaurantBalanceMapper;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import com.delivery.restaurant_service.service.RestaurantBalanceService;

import java.util.List;

public class RestaurantBalanceServiceImpl implements RestaurantBalanceService {

    // mapper
    // private final RestaurantBalanceMapper restaurantBalanceMapper;
    // private final RestaurantRepository restaurantRepository;

    RestaurantBalanceServiceImpl(RestaurantBalanceMapper restaurantBalanceMapper,
                                 RestaurantRepository restaurantRepository) {
        // this.restaurantBalanceMapper = restaurantBalanceMapper;
        // this.restaurantRepository = restaurantRepository;
    }

    @Override
    public List<RestaurantBalance> getAllBalancesByRestaurantId(Long restaurantId) {
        return null;
    }

    @Override
    public RestaurantBalance getRestaurantBalanceById(Long id) {
        return null;
    }

    @Override
    public RestaurantBalance createRestaurantBalance(RestaurantBalance restaurantBalance) {
        return null;
    }

    @Override
    public RestaurantBalance updateRestaurantBalance(Long id, RestaurantBalance restaurantBalance) {
        return null;
    }

    @Override
    public void deleteRestaurantBalance(Long id) {

    }
}
