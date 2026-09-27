package com.delivery.restaurant_service.service;

import com.delivery.restaurant.application.api.CreateMenuItemCommand;
import com.delivery.restaurant.application.api.MenuItemCreateResult;
import com.delivery.restaurant.application.api.MenuItemCreationPort;
import com.delivery.restaurant.application.api.MenuItemMutationPlan;
import com.delivery.restaurant.application.api.MenuItemSnapshot;
import com.delivery.restaurant.application.api.MenuItemCreateDecision;
import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.repository.MenuItemRepository;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Transactional JPA adapter for the framework-free Menu creation port. */
@Component
@RequiredArgsConstructor
public class JpaMenuItemCreationAdapter implements MenuItemCreationPort {

    private final MenuItemRepository menuItemRepository;
    private final RestaurantRepository restaurantRepository;
    private final CatalogCacheSynchronizer cacheSynchronizer;
    private final SearchSyncPublisher searchSyncPublisher;

    @Override
    @Transactional
    public Optional<MenuItemCreateResult> create(
            CreateMenuItemCommand command, MenuItemCreateDecision decision) {
        Optional<Restaurant> parent = restaurantRepository.findById(command.restaurantId());
        if (parent.isEmpty()) {
            return Optional.empty();
        }

        MenuItemMutationPlan plan = decision.decide(new com.delivery.restaurant.domain.ownership.RestaurantManagementFacts(
                parent.get().getOwnerPrincipalId(), parent.get().getCreatorId()));
        boolean claimedLegacyOwnership = parent.get().getOwnerPrincipalId() == null
                && plan.restaurantOwnerPrincipalId() != null;
        parent.get().setOwnerPrincipalId(plan.restaurantOwnerPrincipalId());
        MenuItem item = new MenuItem();
        item.setRestaurant(parent.get());
        apply(plan, item);
        MenuItem saved = menuItemRepository.save(item);

        searchSyncPublisher.publishDishChange(saved, "CREATE");
        cacheSynchronizer.cacheMenuItemAfterCommit(saved);
        return Optional.of(new MenuItemCreateResult(snapshot(saved), claimedLegacyOwnership));
    }

    private static void apply(MenuItemMutationPlan plan, MenuItem item) {
        item.setName(plan.name());
        item.setDescription(plan.description());
        item.setPrice(plan.price());
        item.setStatus(MenuItem.Status.valueOf(plan.status().name()));
        item.setImage(plan.image());
    }

    static MenuItemSnapshot snapshot(MenuItem item) {
        return new MenuItemSnapshot(
                item.getId(),
                item.getRestaurant() == null ? null : item.getRestaurant().getId(),
                item.getName(),
                item.getDescription(),
                item.getPrice(),
                item.getStatus() == null ? null
                        : com.delivery.restaurant.domain.catalog.MenuItemStatus.valueOf(item.getStatus().name()),
                item.getCreatedAt(),
                item.getUpdatedAt(),
                item.getImage(),
                item.getVersion());
    }
}
