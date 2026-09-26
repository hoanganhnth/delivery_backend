package com.delivery.restaurant_service.service.impl;

import com.delivery.restaurant.domain.catalog.RestaurantStatus;
import com.delivery.restaurant.domain.catalog.OperatingSchedule;
import com.delivery.restaurant.application.api.CreateRestaurantCommand;
import com.delivery.restaurant.application.api.CreateRestaurantUseCase;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import com.delivery.restaurant_service.common.constants.RoleConstants;
import com.delivery.restaurant_service.dto.request.CreateRestaurantRequest;
import com.delivery.restaurant_service.dto.request.UpdateRestaurantRequest;
import com.delivery.restaurant_service.dto.request.RestaurantLifecycleRequest;
import com.delivery.restaurant_service.dto.response.RestaurantResponse;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.exception.ResourceNotFoundException;
import com.delivery.restaurant_service.mapper.RestaurantMapper;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import com.delivery.restaurant_service.service.RestaurantService;
import com.delivery.restaurant_service.service.CatalogCacheSynchronizer;
import com.delivery.restaurant_service.service.SearchSyncPublisher;
import com.delivery.restaurant_service.service.ownership.ManagementAccess;
import com.delivery.restaurant_service.service.ownership.RestaurantOwnershipPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.List;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RestaurantServiceImpl implements RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final RestaurantMapper restaurantMapper;
    private final CatalogCacheSynchronizer cacheSynchronizer;
    private final SearchSyncPublisher searchSyncPublisher;
    private final MeterRegistry meterRegistry;
    private final RestaurantOwnershipPolicy restaurantOwnershipPolicy;
    private final CatalogLifecycleService catalogLifecycleService;
    private final CreateRestaurantUseCase createRestaurantUseCase;

    @Override
    public RestaurantResponse createRestaurant(CreateRestaurantRequest request,
            Long creatorId,
            String role) {
        return createRestaurant(request, creatorId, creatorId, role);
    }

    @Override
    public RestaurantResponse createRestaurant(CreateRestaurantRequest request,
            Long actorPrincipalId, Long creatorId, String role) {
        var command = new CreateRestaurantCommand(actorPrincipalId, creatorId, actorRole(role),
                request.getOwnerPrincipalId(), request.getName(), request.getAddress(), request.getPhone(),
                request.getOpeningHour(), request.getClosingHour(), request.getDefaultPrepTimeMinutes(),
                request.getImage(), request.getAddressLat(), request.getAddressLng(), request.getDescription());
        return restaurantMapper.toResponse(createRestaurantUseCase.create(command));
    }

    private RestaurantActorRole actorRole(String role) {
        if (RoleConstants.ADMIN.equalsIgnoreCase(role)) return RestaurantActorRole.ADMIN;
        if (RoleConstants.OWNER.equalsIgnoreCase(role)) return RestaurantActorRole.SHOP_OWNER;
        return RestaurantActorRole.OTHER;
    }

    @Override
    @Transactional
    public RestaurantResponse updateRestaurant(Long id, UpdateRestaurantRequest request, Long creatorId, String role) {
        return updateRestaurant(id, request, creatorId, creatorId, role);
    }

    @Override
    @Transactional
    public RestaurantResponse updateRestaurant(Long id, UpdateRestaurantRequest request,
            Long ownerPrincipalId, Long creatorId, String role) {
        Restaurant existingRestaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
        authorizeWrite(existingRestaurant, ownerPrincipalId, creatorId, role);

        LocalTime openingHour = request.getOpeningHour() != null
                ? request.getOpeningHour() : existingRestaurant.getOpeningHour();
        LocalTime closingHour = request.getClosingHour() != null
                ? request.getClosingHour() : existingRestaurant.getClosingHour();
        validateOperatingHours(openingHour, closingHour, existingRestaurant.getTimeZone());

        restaurantMapper.updateEntityFromDto(request, existingRestaurant);
        Restaurant updated = restaurantRepository.saveAndFlush(existingRestaurant);

        cacheSynchronizer.cacheRestaurantAfterCommit(updated);

        // 🔥 Publish sync event for search service
        searchSyncPublisher.publishRestaurantChange(updated, "UPDATE");

        return restaurantMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void deleteRestaurant(Long id, Long creatorId, String role) {
        deleteRestaurant(id, creatorId, creatorId, role);
    }

    @Override
    @Transactional
    public void deleteRestaurant(Long id, Long ownerPrincipalId, Long creatorId, String role) {
        catalogLifecycleService.archiveRestaurant(id, ownerPrincipalId, creatorId, role);
    }

    @Override
    public RestaurantResponse changeLifecycle(Long id, RestaurantLifecycleRequest request,
            Long ownerPrincipalId, Long creatorId, String role) {
        return catalogLifecycleService.changeRestaurantLifecycle(
                id, request, ownerPrincipalId, creatorId, role);
    }

    @Override
    public RestaurantResponse getRestaurantById(Long id) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Not found"));
        return restaurantMapper.toResponse(restaurant);
    }

    @Override
    public List<RestaurantResponse> getAllRestaurants() {
        List<Restaurant> list = restaurantRepository.findByLifecycleStatusNot(
                RestaurantStatus.ARCHIVED, PageRequest.of(0, 100)).getContent();
        return list.stream()
                .map(restaurantMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<RestaurantResponse> getAllManagedRestaurants() {
        return restaurantRepository.findAll(PageRequest.of(0, 100)).stream()
                .map(restaurantMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public Page<RestaurantResponse> getAllRestaurantsPage(int page, int size, String keyword) {
        PageRequest request = PageRequest.of(page, size);
        Page<Restaurant> source = keyword == null || keyword.isBlank()
                ? restaurantRepository.findByLifecycleStatusNot(RestaurantStatus.ARCHIVED, request)
                : restaurantRepository.findPageByNameContainingIgnoreCaseAndLifecycleStatusNot(
                        keyword.trim(), RestaurantStatus.ARCHIVED, request);
        return source.map(restaurantMapper::toResponse);
    }

    private void authorizeWrite(Restaurant restaurant, Long ownerPrincipalId, Long creatorId, String role) {
        ManagementAccess access = restaurantOwnershipPolicy.assertCanManage(
                restaurant, ownerPrincipalId, creatorId, role);
        if (access.usedLegacyFallback()) {
            identityLegacyFallback("owner_manage");
            if (ownerPrincipalId != null) restaurant.setOwnerPrincipalId(ownerPrincipalId);
        }
    }

    private void validateOperatingHours(LocalTime openingHour, LocalTime closingHour, String timeZone) {
        OperatingSchedule.of(openingHour, closingHour, ZoneId.of(timeZone));
    }

    @Override
    public List<RestaurantResponse> findByName(String keyword) {

        List<Restaurant> restaurants = restaurantRepository
                .findByNameContainingIgnoreCaseAndLifecycleStatusNot(
                        keyword, RestaurantStatus.ARCHIVED, PageRequest.of(0, 100));
        return restaurants.stream()
                .map(restaurantMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<RestaurantResponse> getRestaurantsByCreatorId(Long creatorId) {
        List<Restaurant> restaurants = restaurantRepository.findByCreatorId(
                creatorId, PageRequest.of(0, 100)).getContent();
        return restaurants.stream()
                .map(restaurantMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<RestaurantResponse> getRestaurantsByOwnerPrincipalId(Long ownerPrincipalId, Long legacyCreatorId) {
        var restaurants = restaurantOwnershipPolicy.isPrincipalOwnershipEnforced()
                ? restaurantRepository.findByOwnerPrincipalId(ownerPrincipalId, PageRequest.of(0, 100)).getContent()
                : restaurantRepository.findByOwnerPrincipalOrUnmigratedCreator(
                        ownerPrincipalId, legacyCreatorId, PageRequest.of(0, 100)).getContent();
        restaurants.stream().filter(restaurant -> restaurant.getOwnerPrincipalId() == null)
                .forEach(restaurant -> identityLegacyFallback("owner_list"));
        return restaurants.stream()
                .map(restaurantMapper::toResponse).collect(Collectors.toList());
    }

    private void identityLegacyFallback(String surface) {
        Counter.builder("delivery.identity.legacy.fallback")
                .tag("service", "restaurant").tag("surface", surface)
                .register(meterRegistry).increment();
    }

}
