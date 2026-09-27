package com.delivery.restaurant_service.service;

import com.delivery.restaurant.application.api.MenuItemManagementQuery;
import com.delivery.restaurant.application.api.MenuItemPageSlice;
import com.delivery.restaurant.application.api.MenuItemReadPort;
import com.delivery.restaurant.application.api.MenuItemSnapshot;
import com.delivery.restaurant.application.api.RestaurantManagementAccessUseCase;
import com.delivery.restaurant.domain.catalog.RestaurantStatus;
import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.repository.MenuItemRepository;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter for public AVAILABLE reads and management/history reads. */
@Component
@RequiredArgsConstructor
public class JpaMenuItemReadAdapter implements MenuItemReadPort {

    private final MenuItemRepository menuItemRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantManagementAccessUseCase accessUseCase;

    @Override
    @Transactional(readOnly = true)
    public List<MenuItemSnapshot> findPublicByRestaurant(Long restaurantId, int limit) {
        return menuItemRepository.findByRestaurantIdAndStatusAndRestaurantLifecycleStatusNot(
                        restaurantId, MenuItem.Status.AVAILABLE, RestaurantStatus.ARCHIVED,
                        PageRequest.of(0, limit))
                .stream().map(JpaMenuItemCreationAdapter::snapshot).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MenuItemPageSlice pagePublicByRestaurant(Long restaurantId, int page, int size) {
        Page<MenuItem> source = menuItemRepository
                .findPageByRestaurantIdAndStatusAndRestaurantLifecycleStatusNot(
                        restaurantId, MenuItem.Status.AVAILABLE, RestaurantStatus.ARCHIVED,
                        PageRequest.of(page, size));
        return page(source);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MenuItemPageSlice> findManaged(MenuItemManagementQuery query) {
        if (query.restaurantId() != null) {
            Optional<Restaurant> parent = restaurantRepository.findById(query.restaurantId());
            if (parent.isEmpty()) {
                return Optional.empty();
            }
            accessUseCase.resolve(
                    new com.delivery.restaurant.domain.ownership.RestaurantManagementFacts(
                            parent.get().getOwnerPrincipalId(), parent.get().getCreatorId()),
                    query.principalId(), query.legacyUserId(), query.actorRole(),
                    query.principalOwnershipEnforced());
            return Optional.of(page(menuItemRepository.findPageByRestaurantId(
                    query.restaurantId(), PageRequest.of(query.page(), query.size()))));
        }

        Page<MenuItem> source = query.actorRole()
                == com.delivery.restaurant.domain.ownership.RestaurantActorRole.ADMIN
                ? menuItemRepository.findAll(PageRequest.of(query.page(), query.size()))
                : menuItemRepository.findManagedByOwner(
                        query.principalId(), query.legacyUserId(),
                        !query.principalOwnershipEnforced(),
                        PageRequest.of(query.page(), query.size()));
        return Optional.of(page(source));
    }

    private static MenuItemPageSlice page(Page<MenuItem> source) {
        return new MenuItemPageSlice(
                source.getContent().stream().map(JpaMenuItemCreationAdapter::snapshot).toList(),
                source.getNumber(), source.getSize(), source.getTotalElements(),
                source.getTotalPages(), source.hasNext());
    }
}
