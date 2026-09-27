package com.delivery.restaurant_service.service;

import com.delivery.restaurant.application.api.RestaurantManagementQuery;
import com.delivery.restaurant.application.api.RestaurantManagementResult;
import com.delivery.restaurant.application.api.RestaurantPageSlice;
import com.delivery.restaurant.application.api.RestaurantReadPort;
import com.delivery.restaurant.application.api.RestaurantSnapshot;
import com.delivery.restaurant.domain.catalog.RestaurantStatus;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** JPA implementation of the framework-free Restaurant read boundary. */
@Component
@RequiredArgsConstructor
public class JpaRestaurantReadAdapter implements RestaurantReadPort {

    private static final RestaurantStatus ARCHIVED = RestaurantStatus.ARCHIVED;

    private final RestaurantRepository restaurantRepository;

    @Override
    public Optional<RestaurantSnapshot> findById(Long id) {
        return restaurantRepository.findById(id).map(JpaRestaurantReadAdapter::snapshot);
    }

    @Override
    public List<RestaurantSnapshot> findPublic(int limit) {
        return restaurantRepository.findByLifecycleStatusNot(ARCHIVED, PageRequest.of(0, limit))
                .getContent().stream().map(JpaRestaurantReadAdapter::snapshot).toList();
    }

    @Override
    public List<RestaurantSnapshot> searchPublic(String keyword, int limit) {
        return restaurantRepository.findByNameContainingIgnoreCaseAndLifecycleStatusNot(
                        keyword, ARCHIVED, PageRequest.of(0, limit))
                .stream().map(JpaRestaurantReadAdapter::snapshot).toList();
    }

    @Override
    public RestaurantPageSlice pagePublic(int page, int size, String keyword) {
        Page<Restaurant> source = keyword == null || keyword.isBlank()
                ? restaurantRepository.findByLifecycleStatusNot(ARCHIVED, PageRequest.of(page, size))
                : restaurantRepository.findPageByNameContainingIgnoreCaseAndLifecycleStatusNot(
                        keyword, ARCHIVED, PageRequest.of(page, size));
        return page(source);
    }

    @Override
    public RestaurantManagementResult findManagement(RestaurantManagementQuery query) {
        Page<Restaurant> source;
        if (query.actorRole() == RestaurantActorRole.ADMIN) {
            source = restaurantRepository.findAll(PageRequest.of(0, query.limit()));
        } else if (query.actorRole() == RestaurantActorRole.SHOP_OWNER
                && query.principalOwnershipEnforced()) {
            source = restaurantRepository.findByOwnerPrincipalId(
                    query.principalId(), PageRequest.of(0, query.limit()));
        } else if (query.actorRole() == RestaurantActorRole.SHOP_OWNER) {
            source = restaurantRepository.findByOwnerPrincipalOrUnmigratedCreator(
                    query.principalId(), query.legacyUserId(), PageRequest.of(0, query.limit()));
        } else {
            throw new IllegalArgumentException("Unsupported management actor role");
        }

        List<RestaurantSnapshot> restaurants = source.getContent().stream()
                .map(JpaRestaurantReadAdapter::snapshot).toList();
        int legacyFallbackCount = query.actorRole() == RestaurantActorRole.SHOP_OWNER
                && !query.principalOwnershipEnforced()
                ? source.getContent().stream()
                        .filter(restaurant -> restaurant.getOwnerPrincipalId() == null)
                        .mapToInt(ignored -> 1).sum()
                : 0;
        return new RestaurantManagementResult(restaurants, legacyFallbackCount);
    }

    private static RestaurantPageSlice page(Page<Restaurant> source) {
        List<RestaurantSnapshot> items = source.getContent().stream()
                .map(JpaRestaurantReadAdapter::snapshot).toList();
        return new RestaurantPageSlice(items, source.getNumber(), source.getSize(),
                source.getTotalElements(), source.getTotalPages(), source.hasNext());
    }

    static RestaurantSnapshot snapshot(Restaurant restaurant) {
        return new RestaurantSnapshot(
                restaurant.getId(), restaurant.getName(), restaurant.getAddress(), restaurant.getPhone(),
                restaurant.getOpeningHour(), restaurant.getClosingHour(),
                restaurant.getDefaultPrepTimeMinutes(), restaurant.getImage(), restaurant.getDescription(),
                restaurant.getAddressLat(), restaurant.getAddressLng(), restaurant.getRating(),
                restaurant.getRatingCount(), restaurant.getLifecycleStatus(), restaurant.getVersion(),
                restaurant.getTimeZone(), restaurant.getOwnerPrincipalId(), restaurant.getCreatorId());
    }
}
