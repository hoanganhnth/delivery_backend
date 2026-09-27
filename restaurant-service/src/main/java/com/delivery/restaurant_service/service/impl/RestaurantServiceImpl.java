package com.delivery.restaurant_service.service.impl;

import com.delivery.restaurant.application.api.CreateRestaurantCommand;
import com.delivery.restaurant.application.api.CreateRestaurantUseCase;
import com.delivery.restaurant.application.api.RestaurantManagementReadUseCase;
import com.delivery.restaurant.application.api.RestaurantManagementResult;
import com.delivery.restaurant.application.api.RestaurantPageSlice;
import com.delivery.restaurant.application.api.RestaurantReadUseCase;
import com.delivery.restaurant.application.api.RestaurantSnapshot;
import com.delivery.restaurant.application.api.RestaurantUpdateResult;
import com.delivery.restaurant.application.api.UpdateRestaurantCommand;
import com.delivery.restaurant.application.api.UpdateRestaurantUseCase;
import com.delivery.restaurant.domain.ownership.ManagementAccessException;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import com.delivery.restaurant_service.common.constants.RoleConstants;
import com.delivery.restaurant_service.dto.request.CreateRestaurantRequest;
import com.delivery.restaurant_service.dto.request.RestaurantLifecycleRequest;
import com.delivery.restaurant_service.dto.request.UpdateRestaurantRequest;
import com.delivery.restaurant_service.dto.response.RestaurantResponse;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.exception.ResourceNotFoundException;
import com.delivery.restaurant_service.mapper.RestaurantMapper;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import com.delivery.restaurant_service.service.RestaurantService;
import com.delivery.restaurant_service.service.ownership.RestaurantOwnershipPolicy;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RestaurantServiceImpl implements RestaurantService {

    // Kept for the characterized creator-id compatibility query.
    private final RestaurantRepository restaurantRepository;
    private final RestaurantMapper restaurantMapper;
    private final MeterRegistry meterRegistry;
    private final RestaurantOwnershipPolicy restaurantOwnershipPolicy;
    private final CatalogLifecycleService catalogLifecycleService;
    private final CreateRestaurantUseCase createRestaurantUseCase;
    private final UpdateRestaurantUseCase updateRestaurantUseCase;
    private final RestaurantReadUseCase restaurantReadUseCase;
    private final RestaurantManagementReadUseCase restaurantManagementReadUseCase;

    @Override
    public RestaurantResponse createRestaurant(CreateRestaurantRequest request,
            Long creatorId, String role) {
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

    @Override
    public RestaurantResponse updateRestaurant(Long id, UpdateRestaurantRequest request,
            Long creatorId, String role) {
        return updateRestaurant(id, request, creatorId, creatorId, role);
    }

    @Override
    public RestaurantResponse updateRestaurant(Long id, UpdateRestaurantRequest request,
            Long ownerPrincipalId, Long creatorId, String role) {
        UpdateRestaurantCommand command = new UpdateRestaurantCommand(
                id, ownerPrincipalId, creatorId, actorRole(role),
                restaurantOwnershipPolicy.isPrincipalOwnershipEnforced(),
                request.getName(), request.getAddress(), request.getPhone(),
                request.getOpeningHour(), request.getClosingHour(), request.getDefaultPrepTimeMinutes(),
                request.getImage(), request.getAddressLat(), request.getAddressLng(), request.getDescription());
        RestaurantUpdateResult result;
        try {
            result = updateRestaurantUseCase.update(command)
                    .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
        } catch (ManagementAccessException ex) {
            throw accessDenied();
        }
        if (result.usedLegacyFallback()) {
            identityLegacyFallback("owner_manage");
        }
        return restaurantMapper.toResponse(result.snapshot());
    }

    @Override
    public void deleteRestaurant(Long id, Long creatorId, String role) {
        deleteRestaurant(id, creatorId, creatorId, role);
    }

    @Override
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
        RestaurantSnapshot snapshot = restaurantReadUseCase.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Not found"));
        return restaurantMapper.toResponse(snapshot);
    }

    @Override
    public List<RestaurantResponse> getAllRestaurants() {
        return restaurantReadUseCase.listPublic().stream()
                .map(restaurantMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<RestaurantResponse> getAllManagedRestaurants(Long principalId, Long legacyUserId) {
        RestaurantManagementResult result;
        try {
            result = restaurantManagementReadUseCase.readForAdmin(
                    principalId, legacyUserId,
                    restaurantOwnershipPolicy.isPrincipalOwnershipEnforced());
        } catch (ManagementAccessException ex) {
            throw accessDenied();
        }
        return mapManagementResult(result);
    }

    @Override
    public List<RestaurantResponse> findByName(String keyword) {
        return restaurantReadUseCase.searchPublic(keyword).stream()
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
    public List<RestaurantResponse> getRestaurantsByOwnerPrincipalId(
            Long ownerPrincipalId, Long legacyCreatorId) {
        RestaurantManagementResult result;
        try {
            result = restaurantManagementReadUseCase.readForOwner(
                    ownerPrincipalId, legacyCreatorId,
                    restaurantOwnershipPolicy.isPrincipalOwnershipEnforced());
        } catch (ManagementAccessException ex) {
            throw accessDenied();
        }
        return mapManagementResult(result);
    }

    @Override
    public Page<RestaurantResponse> getAllRestaurantsPage(int page, int size, String keyword) {
        RestaurantPageSlice source = restaurantReadUseCase.pagePublic(page, size, keyword);
        List<RestaurantResponse> items = source.items().stream()
                .map(restaurantMapper::toResponse)
                .toList();
        return new PageImpl<>(items, PageRequest.of(source.page(), source.size()), source.totalItems());
    }

    private List<RestaurantResponse> mapManagementResult(RestaurantManagementResult result) {
        for (int index = 0; index < result.legacyFallbackCount(); index++) {
            identityLegacyFallback("owner_list");
        }
        return result.restaurants().stream()
                .map(restaurantMapper::toResponse)
                .collect(Collectors.toList());
    }

    private RestaurantActorRole actorRole(String role) {
        if (RoleConstants.ADMIN.equalsIgnoreCase(role)) return RestaurantActorRole.ADMIN;
        if (RoleConstants.OWNER.equalsIgnoreCase(role)) return RestaurantActorRole.SHOP_OWNER;
        return RestaurantActorRole.OTHER;
    }

    private AccessDeniedException accessDenied() {
        return new AccessDeniedException("Actor does not own this restaurant");
    }

    private void identityLegacyFallback(String surface) {
        Counter.builder("delivery.identity.legacy.fallback")
                .tag("service", "restaurant").tag("surface", surface)
                .register(meterRegistry).increment();
    }
}
