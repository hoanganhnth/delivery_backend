package com.delivery.restaurant_service.service;

import com.delivery.restaurant.application.api.*;
import com.delivery.restaurant.domain.catalog.*;
import com.delivery.restaurant.domain.ownership.RestaurantManagementFacts;
import com.delivery.restaurant_service.entity.CatalogLifecycleAudit;
import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.repository.*;
import com.delivery.observability.CorrelationContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.OptimisticLockException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/** Persistence, outbox mapping and telemetry for the catalog lifecycle core. */
@Component
@RequiredArgsConstructor
public class JpaCatalogLifecycleAdapter implements CatalogLifecycleStorePort, CatalogLifecycleEffectsPort {
    private final RestaurantRepository restaurants;
    private final MenuItemRepository menus;
    private final CatalogLifecycleAuditRepository audits;
    private final SearchSyncPublisher search;
    private final MeterRegistry metrics;
    @Override public Optional<RestaurantSnapshot> findRestaurant(Long id) {
        return restaurants.findById(id).map(JpaRestaurantReadAdapter::snapshot);
    }
    @Override public Optional<MenuFacts> findMenuItem(Long id) {
        return menus.findById(id).map(row -> new MenuFacts(JpaMenuItemCreationAdapter.snapshot(row),
                new RestaurantManagementFacts(row.getRestaurant().getOwnerPrincipalId(), row.getRestaurant().getCreatorId())));
    }
    @Override public RestaurantSnapshot saveRestaurantStatus(Long id, RestaurantStatus after) {
        return translateConflict(() -> {
            var row = restaurants.findById(id).orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
            row.setLifecycleStatus(after);
            return JpaRestaurantReadAdapter.snapshot(restaurants.saveAndFlush(row));
        });
    }
    @Override public MenuItemSnapshot saveMenuItemStatus(Long id, MenuItemStatus after) {
        return translateConflict(() -> {
            var row = menus.findById(id).orElseThrow(() -> new ResourceNotFoundException("MenuItem not found"));
            row.setStatus(MenuItem.Status.valueOf(after.name()));
            return JpaMenuItemCreationAdapter.snapshot(menus.saveAndFlush(row));
        });
    }
    @Override public void recordAudit(Audit value) {
        translateConflict(() -> {
            var row = new CatalogLifecycleAudit();
            row.setAggregateType(value.aggregateType()); row.setAggregateId(value.aggregateId());
            row.setAction(value.action()); row.setActorPrincipalId(value.actorPrincipalId()); row.setActorRole(value.actorRole());
            row.setBeforeStatus(value.beforeStatus()); row.setAfterStatus(value.afterStatus());
            row.setBeforeVersion(value.beforeVersion()); row.setAfterVersion(value.afterVersion());
            row.setCorrelationId(CorrelationContext.currentOrCreate()); row.setOccurredAt(LocalDateTime.now());
            return audits.save(row);
        });
    }
    @Override public void publishRestaurant(Long id, String action) {
        translateConflict(() -> { search.publishRestaurantChange(restaurants.findById(id).orElseThrow(), action); return null; });
    }
    @Override public void publishMenuItem(Long id, String action) {
        translateConflict(() -> { search.publishDishChange(menus.findById(id).orElseThrow(), action); return null; });
    }
    @Override public void missingExpectedVersion() {
        Counter.builder("delivery.catalog.expected_version.missing").register(metrics).increment();
    }
    @Override public void legacyOwnershipFallback() {
        LoggerFactory.getLogger(JpaCatalogLifecycleAdapter.class).info("Restaurant management used legacy ownership fallback");
    }
    private static <T> T translateConflict(Supplier<T> operation) {
        try { return operation.get(); }
        catch (OptimisticLockException | ObjectOptimisticLockingFailureException conflict) { throw new StaleVersionException(conflict); }
    }
}
