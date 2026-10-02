package com.delivery.restaurant.application;

import com.delivery.restaurant.application.api.*;
import com.delivery.restaurant.domain.catalog.*;
import com.delivery.restaurant.domain.ownership.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefaultCatalogLifecycleUseCaseTest {
    @Test void authorizedTransitionWritesThenAuditsThenPublishesWithWhitelistedFacts() {
        var f = new Fixture(); var result = f.core(false).changeRestaurant(1L, RestaurantStatus.PAUSED, 4L, 10L, 20L, "SHOP_OWNER");
        assertEquals(RestaurantStatus.PAUSED, result.lifecycleStatus()); assertEquals(5L, result.version());
        assertEquals(List.of("transaction", "findRestaurant", "saveRestaurant", "audit", "restaurant:UPDATE"), f.calls);
        assertEquals(new CatalogLifecycleEffectsPort.Audit("RESTAURANT", 1L, "RESTORE_OR_UPDATE", 10L, "SHOP_OWNER", "ACTIVE", "PAUSED", 4L, 5L), f.audit);
    }
    @Test void staleVersionAndForeignOwnerCannotWriteOrPublish() {
        var f = new Fixture();
        assertThrows(StaleVersionException.class, () -> f.core(false).changeRestaurant(1L, RestaurantStatus.PAUSED, 3L, 10L, 20L, "SHOP_OWNER"));
        assertThrows(CatalogAccessDeniedException.class, () -> f.core(false).changeRestaurant(1L, RestaurantStatus.PAUSED, 4L, 11L, 20L, "SHOP_OWNER"));
        assertThrows(CatalogAccessDeniedException.class, () -> f.core(false).changeRestaurant(1L, RestaurantStatus.PAUSED, 4L, 10L, 20L, "UNKNOWN"));
        assertEquals(RestaurantStatus.ACTIVE, f.restaurant.lifecycleStatus()); assertNull(f.audit);
    }
    @Test void idempotentTargetStillChecksVersionAndMissingVersionStillIncrementsMetric() {
        var f = new Fixture();
        assertSame(f.restaurant, f.core(false).changeRestaurant(1L, RestaurantStatus.ACTIVE, null, 10L, 20L, "SHOP_OWNER"));
        assertEquals(1, f.missing); assertNull(f.audit); assertFalse(f.calls.contains("saveRestaurant"));
        assertThrows(StaleVersionException.class, () -> f.core(false).changeRestaurant(1L, RestaurantStatus.ACTIVE, 3L, 10L, 20L, "SHOP_OWNER"));
    }
    @Test void archiveUsesDeleteAndRestoreRequiresAdminWithoutChangingMenuState() {
        var f = new Fixture(); var core = f.core(false);
        core.changeRestaurant(1L, RestaurantStatus.ARCHIVED, 4L, 10L, 20L, "SHOP_OWNER");
        assertEquals(MenuItemStatus.AVAILABLE, f.menu.status()); assertTrue(f.calls.contains("restaurant:DELETE"));
        assertEquals("ARCHIVE", f.audit.action());
        assertThrows(IllegalArgumentException.class, () -> core.changeRestaurant(1L, RestaurantStatus.PAUSED, 5L, 10L, 20L, "SHOP_OWNER"));
        assertEquals(RestaurantStatus.PAUSED, core.changeRestaurant(1L, RestaurantStatus.PAUSED, 5L, 99L, 99L, "admin").lifecycleStatus());
    }
    @Test void menuTransitionsOwnVersionAuditAndRestorePolicy() {
        var f = new Fixture(); var core = f.core(false);
        assertSame(f.menu, core.changeMenuItem(2L, MenuItemStatus.AVAILABLE, 2L, 10L, 20L, "SHOP_OWNER"));
        core.changeMenuItem(2L, MenuItemStatus.ARCHIVED, 2L, 10L, 20L, "SHOP_OWNER");
        assertTrue(f.calls.contains("menu:DELETE")); assertEquals("MENU_ITEM", f.audit.aggregateType());
        assertThrows(IllegalArgumentException.class, () -> core.changeMenuItem(2L, MenuItemStatus.DISCONTINUED, 3L, 10L, 20L, "SHOP_OWNER"));
        assertEquals(MenuItemStatus.DISCONTINUED, core.changeMenuItem(2L, MenuItemStatus.DISCONTINUED, 3L, 99L, 99L, "ADMIN").status());
        assertTrue(f.calls.contains("menu:UPDATE"));
    }
    @Test void legacyOwnershipIsExplicitAndEnforcementDisablesFallback() {
        var f = new Fixture(); f.restaurant = restaurant(RestaurantStatus.ACTIVE, 4L, null, 20L);
        f.core(false).changeRestaurant(1L, RestaurantStatus.PAUSED, 4L, 10L, 20L, "SHOP_OWNER");
        assertEquals(1, f.fallback);
        assertThrows(CatalogAccessDeniedException.class, () -> f.core(true).changeRestaurant(1L, RestaurantStatus.ACTIVE, 5L, 10L, 20L, "SHOP_OWNER"));
    }
    @Test void missingAggregatesAndInvalidTargetsFailBeforeWrite() {
        var f = new Fixture(); f.restaurant = null;
        assertThrows(ResourceNotFoundException.class, () -> f.core(false).changeRestaurant(1L, RestaurantStatus.PAUSED, 4L, 10L, 20L, "SHOP_OWNER"));
        f.menu = null;
        assertThrows(ResourceNotFoundException.class, () -> f.core(false).changeMenuItem(2L, MenuItemStatus.ARCHIVED, 2L, 10L, 20L, "SHOP_OWNER"));
        f.restaurant = restaurant(RestaurantStatus.ACTIVE, null, 10L, 20L);
        assertThrows(StaleVersionException.class, () -> f.core(false).changeRestaurant(1L, RestaurantStatus.PAUSED, 4L, 10L, 20L, "SHOP_OWNER"));
        assertThrows(IllegalArgumentException.class, () -> f.core(false).changeRestaurant(1L, null, null, 10L, 20L, "SHOP_OWNER"));
        f.core(false).changeRestaurant(1L, RestaurantStatus.PAUSED, null, 10L, 20L, "SHOP_OWNER");
        assertEquals(0, f.audit.beforeVersion());
    }
    private static RestaurantSnapshot restaurant(RestaurantStatus status, Long version, Long principal, Long creator) {
        return new RestaurantSnapshot(1L, "R", null, null, null, null, null, null, null, null, null, null, null, status, version, "UTC", principal, creator);
    }
    private static MenuItemSnapshot menu(MenuItemStatus status, Long version) {
        return new MenuItemSnapshot(2L, 1L, "M", null, BigDecimal.TEN, status, null, null, null, version);
    }
    private static class Fixture implements CatalogLifecycleStorePort, CatalogLifecycleEffectsPort, RestaurantTransactionPort {
        RestaurantSnapshot restaurant = restaurant(RestaurantStatus.ACTIVE, 4L, 10L, 20L);
        MenuItemSnapshot menu = menu(MenuItemStatus.AVAILABLE, 2L);
        Audit audit; int missing, fallback; List<String> calls = new ArrayList<>();
        DefaultCatalogLifecycleUseCase core(boolean enforced) { return new DefaultCatalogLifecycleUseCase(this, this,
                new DefaultCatalogLifecycleDecisionUseCase(new RestaurantLifecyclePolicy(), new MenuItemLifecyclePolicy()),
                new DefaultRestaurantManagementAccessUseCase(), this, enforced); }
        public <T> T required(Supplier<T> operation) { calls.add("transaction"); return operation.get(); }
        public <T> T repeatableRead(java.util.function.Supplier<T> operation) { return operation.get(); }
        public <T> T readOnly(Supplier<T> operation) { return operation.get(); }
        public Optional<RestaurantSnapshot> findRestaurant(Long id) { calls.add("findRestaurant"); return Optional.ofNullable(restaurant); }
        public Optional<MenuFacts> findMenuItem(Long id) { return menu == null ? Optional.empty() : Optional.of(new MenuFacts(menu, new RestaurantManagementFacts(10L,20L))); }
        public RestaurantSnapshot saveRestaurantStatus(Long id, RestaurantStatus after) { calls.add("saveRestaurant"); restaurant = restaurant(after, restaurant.version() == null ? 1L : restaurant.version()+1, restaurant.ownerPrincipalId(), restaurant.creatorId()); return restaurant; }
        public MenuItemSnapshot saveMenuItemStatus(Long id, MenuItemStatus after) { menu = menu(after, menu.version()+1); return menu; }
        public void recordAudit(Audit value) { calls.add("audit"); audit = value; }
        public void publishRestaurant(Long id, String action) { calls.add("restaurant:"+action); }
        public void publishMenuItem(Long id, String action) { calls.add("menu:"+action); }
        public void missingExpectedVersion() { missing++; }
        public void legacyOwnershipFallback() { fallback++; }
    }
}
