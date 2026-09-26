package com.delivery.restaurant_service.service;

import com.delivery.restaurant.application.api.CreateRestaurantCommand;
import com.delivery.restaurant.application.api.CreateRestaurantResult;
import com.delivery.restaurant.application.api.RestaurantCreationPort;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Kept in the infrastructure's existing scanned package for host discovery. */
@Component
@RequiredArgsConstructor
@Slf4j
public class JpaRestaurantCreationAdapter implements RestaurantCreationPort {
    private final RestaurantRepository restaurantRepository;
    private final CatalogCacheSynchronizer cacheSynchronizer;
    private final SearchSyncPublisher searchSyncPublisher;

    @Override
    @Transactional
    public CreateRestaurantResult create(CreateRestaurantCommand command, long ownerPrincipalId) {
        Restaurant restaurant = new Restaurant();
        restaurant.setName(command.name());
        restaurant.setAddress(command.address());
        restaurant.setPhone(command.phone());
        restaurant.setDescription(command.description());
        restaurant.setOpeningHour(command.openingHour());
        restaurant.setClosingHour(command.closingHour());
        if (command.defaultPrepTimeMinutes() != null) {
            restaurant.setDefaultPrepTimeMinutes(command.defaultPrepTimeMinutes());
        }
        restaurant.setImage(command.image());
        restaurant.setAddressLat(command.addressLat());
        restaurant.setAddressLng(command.addressLng());
        restaurant.setCreatorId(command.creatorId());
        restaurant.setOwnerPrincipalId(ownerPrincipalId);
        Restaurant saved = restaurantRepository.save(restaurant);

        // Preserve the existing log-only initial-balance behavior; no balance subsystem is invoked.
        try {
            log.info("✅ Created initial balance for restaurant: {} (ID: {})", saved.getName(), saved.getId());
        } catch (Exception e) {
            log.warn("⚠️ Failed to create initial balance for restaurant: {}", e.getMessage());
        }
        cacheSynchronizer.cacheRestaurantAfterCommit(saved);
        searchSyncPublisher.publishRestaurantChange(saved, "CREATE");
        return new CreateRestaurantResult(saved.getId(), saved.getName(), saved.getAddress(), saved.getPhone(),
                saved.getOpeningHour(), saved.getClosingHour(), saved.getDefaultPrepTimeMinutes(),
                saved.getImage(), saved.getDescription(), saved.getAddressLat(), saved.getAddressLng(),
                saved.getRating(), saved.getRatingCount(), saved.getLifecycleStatus(), saved.getVersion(),
                saved.getTimeZone());
    }
}
