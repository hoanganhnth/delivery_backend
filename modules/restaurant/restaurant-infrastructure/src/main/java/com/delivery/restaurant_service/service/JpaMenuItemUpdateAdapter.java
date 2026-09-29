package com.delivery.restaurant_service.service;

import com.delivery.restaurant.application.api.MenuItemMutationPlan;
import com.delivery.restaurant.application.api.MenuItemStoredFacts;
import com.delivery.restaurant.application.api.MenuItemUpdateDecision;
import com.delivery.restaurant.application.api.MenuItemUpdatePort;
import com.delivery.restaurant.application.api.MenuItemUpdateResult;
import com.delivery.restaurant.application.api.UpdateMenuItemCommand;
import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.repository.MenuItemRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Keeps Menu load, application decision, mutation and outbox in one transaction. */
@Component
@RequiredArgsConstructor
public class JpaMenuItemUpdateAdapter implements MenuItemUpdatePort {

    private final MenuItemRepository menuItemRepository;
    private final SearchSyncPublisher searchSyncPublisher;

    @Override
    @Transactional
    public Optional<MenuItemUpdateResult> update(
            UpdateMenuItemCommand command, MenuItemUpdateDecision decision) {
        Optional<MenuItem> loaded = menuItemRepository.findById(command.menuItemId());
        if (loaded.isEmpty()) {
            return Optional.empty();
        }

        MenuItem item = loaded.get();
        MenuItemStoredFacts storedFacts = storedFacts(item);
        MenuItemMutationPlan plan = decision.decide(storedFacts);
        apply(plan, item);
        MenuItem saved = menuItemRepository.saveAndFlush(item);

        searchSyncPublisher.publishDishChange(saved, "UPDATE");
        return Optional.of(new MenuItemUpdateResult(
                JpaMenuItemCreationAdapter.snapshot(saved),
                storedFacts.restaurantOwnerPrincipalId() == null
                        && plan.restaurantOwnerPrincipalId() != null));
    }

    private static MenuItemStoredFacts storedFacts(MenuItem item) {
        var restaurant = item.getRestaurant();
        return new MenuItemStoredFacts(
                item.getId(),
                restaurant == null ? null : restaurant.getId(),
                restaurant == null ? null : restaurant.getOwnerPrincipalId(),
                restaurant == null ? null : restaurant.getCreatorId(),
                item.getName(),
                item.getDescription(),
                item.getPrice(),
                item.getStatus() == null ? null
                        : com.delivery.restaurant.domain.catalog.MenuItemStatus.valueOf(item.getStatus().name()),
                item.getImage(),
                item.getCreatedAt(),
                item.getUpdatedAt(),
                item.getVersion());
    }

    private static void apply(MenuItemMutationPlan plan, MenuItem item) {
        // The parent is intentionally not assigned from the command or plan.
        // Menu ownership is inherited and a Menu item cannot be re-parented here.
        if (item.getRestaurant() != null) {
            item.getRestaurant().setOwnerPrincipalId(plan.restaurantOwnerPrincipalId());
        }
        item.setName(plan.name());
        item.setDescription(plan.description());
        item.setPrice(plan.price());
        item.setStatus(MenuItem.Status.valueOf(plan.status().name()));
        item.setImage(plan.image());
    }
}
