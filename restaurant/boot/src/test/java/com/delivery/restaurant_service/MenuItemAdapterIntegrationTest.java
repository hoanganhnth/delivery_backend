package com.delivery.restaurant_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.delivery.identity.client.IdentityPrincipalClient;
import com.delivery.restaurant.application.api.CreateMenuItemCommand;
import com.delivery.restaurant.application.api.CreateMenuItemUseCase;
import com.delivery.restaurant.application.api.MenuItemManagementQuery;
import com.delivery.restaurant.application.api.MenuItemManagementReadUseCase;
import com.delivery.restaurant.application.api.MenuItemPageSlice;
import com.delivery.restaurant.application.api.MenuItemReadUseCase;
import com.delivery.restaurant.application.api.MenuItemSnapshot;
import com.delivery.restaurant.application.api.UpdateMenuItemCommand;
import com.delivery.restaurant.application.api.UpdateMenuItemUseCase;
import com.delivery.restaurant.domain.catalog.MenuItemStatus;
import com.delivery.restaurant.domain.catalog.RestaurantStatus;
import com.delivery.restaurant.domain.ownership.ManagementAccessException;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.repository.MenuItemRepository;
import com.delivery.restaurant_service.repository.RestaurantOutboxEventRepository;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import com.delivery.restaurant_service.service.SearchSyncPublisher;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

/** H2 proof for Menu adapters; it does not prove PostgreSQL concurrency or crash atomicity. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:menu-adapters;DB_CLOSE_DELAY=-1",
        "app.search-sync.enabled=true"
})
@ActiveProfiles("test")
class MenuItemAdapterIntegrationTest {

    @Autowired CreateMenuItemUseCase createMenuItem;
    @Autowired UpdateMenuItemUseCase updateMenuItem;
    @Autowired MenuItemReadUseCase publicReads;
    @Autowired MenuItemManagementReadUseCase managementReads;
    @Autowired MenuItemRepository menuItems;
    @Autowired RestaurantRepository restaurants;
    @Autowired RestaurantOutboxEventRepository outbox;
    @MockitoBean IdentityPrincipalClient identity;
    @MockitoSpyBean SearchSyncPublisher search;

    @AfterEach
    void cleanDatabase() {
        outbox.deleteAll();
        menuItems.deleteAll();
        restaurants.deleteAll();
    }

    @Test
    void createPersistsAvailableItemAndSearchCreateOutbox() {
        Restaurant restaurant = saveRestaurant(7L, 700L, RestaurantStatus.ACTIVE);

        var result = createMenuItem.create(new CreateMenuItemCommand(
                restaurant.getId(), 7L, 700L, RestaurantActorRole.SHOP_OWNER, true,
                "Pho", "Classic", new BigDecimal("55000.00"), "pho.png"));

        MenuItemSnapshot snapshot = result.orElseThrow().snapshot();
        assertThat(snapshot.id()).isNotNull();
        assertThat(snapshot.restaurantId()).isEqualTo(restaurant.getId());
        assertThat(snapshot.price()).isEqualByComparingTo("55000.00");
        assertThat(snapshot.status()).isEqualTo(MenuItemStatus.AVAILABLE);
        assertThat(restaurants.findById(restaurant.getId()).orElseThrow().getOwnerPrincipalId())
                .isEqualTo(7L);
        assertThat(outbox.findAll()).extracting("eventType")
                .containsExactly("SEARCH_DISH_CREATE");
    }

    @Test
    void updatePreservesParentAndWritesUpdateOutbox() {
        Restaurant restaurant = saveRestaurant(null, 700L, RestaurantStatus.PAUSED);
        MenuItem item = saveMenuItem(restaurant, "Old", MenuItem.Status.AVAILABLE);

        var result = updateMenuItem.update(new UpdateMenuItemCommand(
                item.getId(), 101L, 700L, RestaurantActorRole.SHOP_OWNER, false,
                "Updated", null, new BigDecimal("65000.00"), MenuItemStatus.SOLD_OUT,
                "updated.png", 999L));

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().snapshot().restaurantId()).isEqualTo(restaurant.getId());
        MenuItem saved = menuItems.findById(item.getId()).orElseThrow();
        assertThat(saved.getName()).isEqualTo("Updated");
        assertThat(saved.getDescription()).isEqualTo("Description");
        assertThat(saved.getPrice()).isEqualByComparingTo("65000.00");
        assertThat(saved.getStatus()).isEqualTo(MenuItem.Status.SOLD_OUT);
        assertThat(saved.getRestaurant().getId()).isEqualTo(restaurant.getId());
        assertThat(restaurants.findById(restaurant.getId()).orElseThrow().getOwnerPrincipalId())
                .isEqualTo(101L);
        assertThat(outbox.findAll()).extracting("eventType")
                .containsExactly("SEARCH_DISH_UPDATE");
    }

    @Test
    void publicReadsReturnAvailableItemsUnderActiveOrPausedParentsOnly() {
        Restaurant active = saveRestaurant(7L, 700L, RestaurantStatus.ACTIVE);
        Restaurant paused = saveRestaurant(7L, 700L, RestaurantStatus.PAUSED);
        Restaurant archived = saveRestaurant(7L, 700L, RestaurantStatus.ARCHIVED);
        saveMenuItem(active, "Available", MenuItem.Status.AVAILABLE);
        saveMenuItem(active, "Sold out", MenuItem.Status.SOLD_OUT);
        saveMenuItem(paused, "Paused visible", MenuItem.Status.AVAILABLE);
        saveMenuItem(archived, "Archived hidden", MenuItem.Status.AVAILABLE);

        List<MenuItemSnapshot> items = publicReads.listPublic(active.getId());
        assertThat(items).extracting(MenuItemSnapshot::name).containsExactly("Available");
        assertThat(publicReads.listPublic(paused.getId()))
                .extracting(MenuItemSnapshot::name).containsExactly("Paused visible");
        assertThat(publicReads.listPublic(archived.getId())).isEmpty();
        assertThat(publicReads.listPublic(999999L)).isEmpty();

        MenuItemPageSlice page = publicReads.pagePublic(paused.getId(), 0, 24);
        assertThat(page.items()).extracting(MenuItemSnapshot::name)
                .containsExactly("Paused visible");
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(24);
        assertThat(page.totalItems()).isEqualTo(1L);
    }

    @Test
    void managementReadsPreserveAdminHistoryPrincipalPrecedenceLegacyFallbackAndParentErrors() {
        Restaurant own = saveRestaurant(101L, 7L, RestaurantStatus.ACTIVE);
        Restaurant foreignSameLegacy = saveRestaurant(202L, 7L, RestaurantStatus.ACTIVE);
        Restaurant legacy = saveRestaurant(null, 7L, RestaurantStatus.ARCHIVED);
        saveMenuItem(own, "Own", MenuItem.Status.AVAILABLE);
        saveMenuItem(foreignSameLegacy, "Foreign", MenuItem.Status.AVAILABLE);
        saveMenuItem(legacy, "Legacy", MenuItem.Status.ARCHIVED);

        Optional<MenuItemPageSlice> admin = managementReads.read(new MenuItemManagementQuery(
                null, 900L, 900L, RestaurantActorRole.ADMIN, true, 0, 100));
        assertThat(admin).isPresent();
        assertThat(admin.orElseThrow().totalItems()).isEqualTo(3L);

        Optional<MenuItemPageSlice> owner = managementReads.read(new MenuItemManagementQuery(
                null, 101L, 7L, RestaurantActorRole.SHOP_OWNER, false, 0, 100));
        assertThat(owner).isPresent();
        assertThat(owner.orElseThrow().items()).extracting(MenuItemSnapshot::name)
                .containsExactlyInAnyOrder("Own", "Legacy");

        assertThat(managementReads.read(new MenuItemManagementQuery(
                999999L, 101L, 7L, RestaurantActorRole.SHOP_OWNER, true, 0, 24)))
                .isEmpty();
        assertThatThrownBy(() -> managementReads.read(new MenuItemManagementQuery(
                foreignSameLegacy.getId(), 101L, 7L, RestaurantActorRole.SHOP_OWNER,
                true, 0, 24))).isInstanceOf(ManagementAccessException.class);
    }

    @Test
    void searchFailureRollsBackMenuUpdateAndOutbox() {
        Restaurant restaurant = saveRestaurant(7L, 700L, RestaurantStatus.ACTIVE);
        MenuItem item = saveMenuItem(restaurant, "Original", MenuItem.Status.AVAILABLE);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("Injected menu update outbox failure");
        }).when(AopTestUtils.<SearchSyncPublisher>getUltimateTargetObject(search))
                .publishDishChange(any(), eq("UPDATE"));

        assertThatThrownBy(() -> updateMenuItem.update(new UpdateMenuItemCommand(
                item.getId(), 7L, 700L, RestaurantActorRole.SHOP_OWNER, true,
                "Must Roll Back", null, null, null, null, null)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(menuItems.findById(item.getId()).orElseThrow().getName())
                .isEqualTo("Original");
        assertThat(outbox.count()).isZero();
    }

    private Restaurant saveRestaurant(Long ownerPrincipalId, Long creatorId, RestaurantStatus status) {
        Restaurant restaurant = new Restaurant();
        restaurant.setName("Restaurant " + System.nanoTime());
        restaurant.setAddress("123 Main Street");
        restaurant.setOwnerPrincipalId(ownerPrincipalId);
        restaurant.setCreatorId(creatorId);
        restaurant.setLifecycleStatus(status);
        return restaurants.saveAndFlush(restaurant);
    }

    private MenuItem saveMenuItem(Restaurant restaurant, String name, MenuItem.Status status) {
        MenuItem item = new MenuItem();
        item.setRestaurant(restaurant);
        item.setName(name);
        item.setDescription("Description");
        item.setPrice(new BigDecimal("50000.00"));
        item.setStatus(status);
        return menuItems.saveAndFlush(item);
    }
}
