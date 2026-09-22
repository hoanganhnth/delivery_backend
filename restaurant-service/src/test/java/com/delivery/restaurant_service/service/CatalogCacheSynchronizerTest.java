package com.delivery.restaurant_service.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.delivery.restaurant_service.entity.Restaurant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class CatalogCacheSynchronizerTest {

    private final RestaurantCacheService cache = org.mockito.Mockito.mock(RestaurantCacheService.class);
    private final CatalogCacheSynchronizer synchronizer = new CatalogCacheSynchronizer(cache);

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void cacheMutationRunsOnlyAfterDatabaseCommit() {
        Restaurant restaurant = new Restaurant();
        beginTransactionSynchronization();

        synchronizer.cacheRestaurantAfterCommit(restaurant);

        verify(cache, never()).cacheRestaurant(restaurant);
        commit();
        verify(cache).cacheRestaurant(restaurant);
    }

    @Test
    void rollbackDoesNotRunCacheMutation() {
        beginTransactionSynchronization();

        synchronizer.removeRestaurantAfterCommit(9L);

        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(cache, never()).removeRestaurantFromCache(9L);
    }

    @Test
    void cacheFailureAfterCommitDoesNotChangeCommittedBusinessOutcome() {
        Restaurant restaurant = new Restaurant();
        beginTransactionSynchronization();
        doThrow(new IllegalStateException("redis unavailable")).when(cache).cacheRestaurant(restaurant);
        synchronizer.cacheRestaurantAfterCommit(restaurant);

        assertDoesNotThrow(this::commit);
    }

    @Test
    void schedulingWithoutTransactionFailsBeforeAnyCacheMutation() {
        Restaurant restaurant = new Restaurant();

        assertThrows(IllegalStateException.class,
                () -> synchronizer.cacheRestaurantAfterCommit(restaurant));

        verify(cache, never()).cacheRestaurant(restaurant);
    }

    private void beginTransactionSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    private void commit() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        complete(TransactionSynchronization.STATUS_COMMITTED);
    }

    private void complete(int status) {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(status));
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }
}
