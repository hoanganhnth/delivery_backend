package com.delivery.restaurant_service.service;

import com.delivery.restaurant.application.api.RestaurantMutationPlan;
import com.delivery.restaurant.application.api.RestaurantSnapshot;
import com.delivery.restaurant.application.api.RestaurantStoredFacts;
import com.delivery.restaurant.application.api.RestaurantUpdateDecision;
import com.delivery.restaurant.application.api.RestaurantUpdatePort;
import com.delivery.restaurant.application.api.RestaurantUpdateResult;
import com.delivery.restaurant.application.api.UpdateRestaurantCommand;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Keeps load, application decision, update and Search outbox in one transaction. */
@Component
@RequiredArgsConstructor
public class JpaRestaurantUpdateAdapter implements RestaurantUpdatePort {

    private final RestaurantRepository restaurantRepository;
    private final SearchSyncPublisher searchSyncPublisher;

    @Override
    @Transactional
    public Optional<RestaurantUpdateResult> update(UpdateRestaurantCommand command,
            RestaurantUpdateDecision decision) {
        Optional<Restaurant> loaded = restaurantRepository.findByIdForUpdate(command.restaurantId());
        if (loaded.isEmpty()) {
            return Optional.empty();
        }

        Restaurant restaurant = loaded.get();
        RestaurantStoredFacts storedFacts = storedFacts(restaurant);
        RestaurantMutationPlan plan = decision.decide(storedFacts);
        apply(plan, restaurant);
        Restaurant saved = restaurantRepository.saveAndFlush(restaurant);

        // The outbox write is mandatory and participates in this transaction.
        searchSyncPublisher.publishRestaurantChange(saved, "UPDATE");
        return Optional.of(new RestaurantUpdateResult(
                JpaRestaurantReadAdapter.snapshot(saved),
                storedFacts.ownerPrincipalId() == null && plan.ownerPrincipalId() != null));
    }

    private static RestaurantStoredFacts storedFacts(Restaurant restaurant) {
        return new RestaurantStoredFacts(
                restaurant.getId(), restaurant.getOwnerPrincipalId(), restaurant.getCreatorId(),
                restaurant.getName(), restaurant.getAddress(), restaurant.getPhone(),
                restaurant.getOpeningHour(), restaurant.getClosingHour(),
                restaurant.getDefaultPrepTimeMinutes(), restaurant.getImage(), restaurant.getDescription(),
                restaurant.getAddressLat(), restaurant.getAddressLng(), restaurant.getTimeZone());
    }

    private static void apply(RestaurantMutationPlan plan, Restaurant restaurant) {
        restaurant.setOwnerPrincipalId(plan.ownerPrincipalId());
        restaurant.setName(plan.name());
        restaurant.setAddress(plan.address());
        restaurant.setPhone(plan.phone());
        restaurant.setOpeningHour(plan.openingHour());
        restaurant.setClosingHour(plan.closingHour());
        restaurant.setDefaultPrepTimeMinutes(plan.defaultPrepTimeMinutes());
        restaurant.setImage(plan.image());
        restaurant.setDescription(plan.description());
        restaurant.setAddressLat(plan.latitude());
        restaurant.setAddressLng(plan.longitude());
    }
}
