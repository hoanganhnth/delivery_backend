package com.delivery.restaurant_service.service;

import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.entity.Restaurant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogCacheSynchronizer {

    private final RestaurantCacheService cacheService;

    public void cacheRestaurantAfterCommit(Restaurant restaurant) {
        afterCommit("cache restaurant " + restaurant.getId(),
                () -> cacheService.cacheRestaurant(restaurant));
    }

    public void removeRestaurantAfterCommit(Long restaurantId) {
        afterCommit("remove restaurant " + restaurantId,
                () -> cacheService.removeRestaurantFromCache(restaurantId));
    }

    public void cacheMenuItemAfterCommit(MenuItem item) {
        afterCommit("cache menu item " + item.getId(),
                () -> cacheService.cacheMenuItem(item));
    }

    public void removeMenuItemAfterCommit(Long menuItemId) {
        afterCommit("remove menu item " + menuItemId,
                () -> cacheService.removeMenuItemFromCache(menuItemId));
    }

    private void afterCommit(String operation, Runnable mutation) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Catalog cache mutation requires an active transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    mutation.run();
                } catch (RuntimeException exception) {
                    log.warn("Catalog cache synchronization failed after commit: operation={}",
                            operation, exception);
                }
            }
        });
    }
}
