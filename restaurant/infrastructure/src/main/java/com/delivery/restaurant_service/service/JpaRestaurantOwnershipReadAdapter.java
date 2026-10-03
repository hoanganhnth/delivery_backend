package com.delivery.restaurant_service.service;

import com.delivery.restaurant.application.api.RestaurantOwnershipReadPort;
import com.delivery.restaurant.domain.ownership.RestaurantManagementFacts;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class JpaRestaurantOwnershipReadAdapter implements RestaurantOwnershipReadPort {
    private final RestaurantRepository restaurants;
    public JpaRestaurantOwnershipReadAdapter(RestaurantRepository restaurants) { this.restaurants = restaurants; }
    @Override public Optional<RestaurantManagementFacts> findOwnership(Long id) {
        return restaurants.findById(id).map(row -> new RestaurantManagementFacts(row.getOwnerPrincipalId(), row.getCreatorId()));
    }
}
