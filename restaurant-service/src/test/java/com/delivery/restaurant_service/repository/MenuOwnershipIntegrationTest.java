package com.delivery.restaurant_service.repository;

import com.delivery.restaurant.application.DefaultCreateMenuItemUseCase;
import com.delivery.restaurant.application.DefaultMenuItemManagementReadUseCase;
import com.delivery.restaurant.application.DefaultMenuItemReadUseCase;
import com.delivery.restaurant.application.DefaultRestaurantManagementAccessUseCase;
import com.delivery.restaurant.application.DefaultUpdateMenuItemUseCase;
import com.delivery.restaurant.application.api.RestaurantManagementAccessUseCase;
import com.delivery.restaurant_service.entity.*;
import com.delivery.restaurant_service.mapper.MenuItemMapper;
import com.delivery.restaurant_service.service.*;
import com.delivery.restaurant_service.service.impl.MenuItemServiceImpl;
import com.delivery.restaurant_service.mapper.RestaurantMapper;
import com.delivery.restaurant_service.service.impl.CatalogLifecycleService;
import com.delivery.restaurant.domain.catalog.MenuItemLifecyclePolicy;
import com.delivery.restaurant.domain.catalog.RestaurantLifecyclePolicy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.delivery.restaurant_service.service.ownership.RestaurantOwnershipPolicy;
import com.delivery.restaurant_service.dto.request.*;
import com.delivery.restaurant_service.service.JpaMenuItemCreationAdapter;
import com.delivery.restaurant_service.service.JpaMenuItemReadAdapter;
import com.delivery.restaurant_service.service.JpaMenuItemUpdateAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@DataJpaTest
@ActiveProfiles("test")
class MenuOwnershipIntegrationTest {
    @Autowired MenuItemRepository items;
    @Autowired RestaurantRepository restaurants;
    @Autowired TestEntityManager em;
    @Autowired CatalogLifecycleAuditRepository audits;

    private MenuItemServiceImpl service(boolean enforced) {
        RestaurantManagementAccessUseCase accessUseCase = new DefaultRestaurantManagementAccessUseCase();
        var cache = mock(CatalogCacheSynchronizer.class);
        var search = mock(SearchSyncPublisher.class);
        var create = new DefaultCreateMenuItemUseCase(
                new JpaMenuItemCreationAdapter(items, restaurants, cache, search), accessUseCase);
        var update = new DefaultUpdateMenuItemUseCase(
                new JpaMenuItemUpdateAdapter(items, cache, search), accessUseCase);
        var reads = new DefaultMenuItemReadUseCase(
                new JpaMenuItemReadAdapter(items, restaurants, accessUseCase));
        var managementReads = new DefaultMenuItemManagementReadUseCase(
                new JpaMenuItemReadAdapter(items, restaurants, accessUseCase));
        return new MenuItemServiceImpl(items, new MenuItemMapper(),
                new RestaurantOwnershipPolicy(enforced), new CatalogLifecycleService(
                        restaurants, items, new RestaurantMapper(), new MenuItemMapper(),
                        mock(CatalogCacheSynchronizer.class), mock(SearchSyncPublisher.class),
                        new RestaurantOwnershipPolicy(enforced), new RestaurantLifecyclePolicy(),
                        new MenuItemLifecyclePolicy(), audits, new SimpleMeterRegistry()),
                create, update, reads, managementReads);
    }
    private MenuItem seed(Long principal, long legacy, MenuItem.Status status) {
        var restaurant = new Restaurant();
        restaurant.setName("Restaurant"); restaurant.setCreatorId(legacy);
        restaurant.setOwnerPrincipalId(principal);
        restaurants.saveAndFlush(restaurant);
        var item = new MenuItem(); item.setName("Meal"); item.setPrice(BigDecimal.valueOf(50000));
        item.setRestaurant(restaurant); item.setStatus(status);
        return items.saveAndFlush(item);
    }
    @Test void listAndPageExcludeForeignPrincipalEvenWithSameLegacyId() {
        var own = seed(101L, 7, MenuItem.Status.SOLD_OUT);
        seed(202L, 7, MenuItem.Status.AVAILABLE);
        var legacy = seed(null, 7, MenuItem.Status.DISCONTINUED);
        var archived = seed(101L, 8, MenuItem.Status.ARCHIVED);
        var result = service(false).getManagedItemsPage(null, 101L, 7L, "SHOP_OWNER", 0, 100);
        assertEquals(java.util.Set.of(own.getId(), legacy.getId(), archived.getId()),
                result.map(r -> r.getId()).stream().collect(java.util.stream.Collectors.toSet()));
        assertEquals(3, result.getTotalElements());
        var enforced = service(true).getManagedItemsPage(null, 101L, 7L, "SHOP_OWNER", 0, 1);
        assertEquals(2, enforced.getTotalElements());
        assertEquals(1, enforced.getContent().size());
        assertTrue(java.util.Set.of(own.getId(), archived.getId())
                .contains(enforced.getContent().get(0).getId()));
        assertEquals(4, service(true).getManagedItemsPage(null, 999L, null, "ADMIN", 0, 100).getTotalElements());
    }
    @Test void foreignReadCreateUpdateDeleteAreRejectedWithoutChangingData() {
        var foreign = seed(202L, 7, MenuItem.Status.AVAILABLE);
        var restaurantId = foreign.getRestaurant().getId();
        var service = service(false);
        assertThrows(AccessDeniedException.class, () -> service.getManagedItemsPage(restaurantId, 101L, 7L, "SHOP_OWNER", 0, 24));
        var create = new CreateMenuItemRequest(); create.setRestaurantId(restaurantId);
        create.setName("Intruder"); create.setPrice(BigDecimal.ONE);
        assertThrows(AccessDeniedException.class, () -> service.createMenuItem(create, 101L, 7L, "SHOP_OWNER"));
        var update = new UpdateMenuItemRequest(); update.setName("Intruder"); update.setRestaurantId(999L);
        assertThrows(AccessDeniedException.class, () -> service.updateMenuItem(foreign.getId(), update, 101L, 7L, "SHOP_OWNER"));
        assertThrows(AccessDeniedException.class, () -> service.deleteMenuItem(foreign.getId(), 101L, 7L, "SHOP_OWNER"));
        assertEquals("Meal", items.findById(foreign.getId()).orElseThrow().getName());
        assertEquals(1, items.count());
    }
    @Test void legacyWritePersistsPrincipalButReadDoesNotMigrate() {
        var item = seed(null, 7, MenuItem.Status.AVAILABLE);
        var restaurantId = item.getRestaurant().getId();
        service(false).getManagedItemsPage(restaurantId, 101L, 7L, "SHOP_OWNER", 0, 24);
        assertNull(item.getRestaurant().getOwnerPrincipalId());
        var update = new UpdateMenuItemRequest(); update.setName("Updated");
        service(false).updateMenuItem(item.getId(), update, 101L, 7L, "SHOP_OWNER");
        em.flush(); em.clear();
        assertEquals(101L, restaurants.findById(restaurantId).orElseThrow().getOwnerPrincipalId());
    }
    @Test void userRoleCannotManageEvenWhenIdentityMatches() {
        var own = seed(101L, 7, MenuItem.Status.AVAILABLE);
        assertThrows(AccessDeniedException.class, () -> service(false).getManagedItemsPage(null, 101L, 7L, "USER", 0, 24));
        assertThrows(AccessDeniedException.class, () -> service(false).deleteMenuItem(own.getId(), 101L, 7L, "USER"));
    }
}
