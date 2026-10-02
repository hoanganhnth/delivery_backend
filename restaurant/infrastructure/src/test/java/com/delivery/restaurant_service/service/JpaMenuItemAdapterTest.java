package com.delivery.restaurant_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.delivery.restaurant.application.api.CreateMenuItemCommand;
import com.delivery.restaurant.application.api.MenuItemManagementQuery;
import com.delivery.restaurant.application.api.MenuItemMutationPlan;
import com.delivery.restaurant.application.api.MenuItemPageSlice;
import com.delivery.restaurant.application.api.MenuItemSnapshot;
import com.delivery.restaurant.application.api.MenuItemUpdateResult;
import com.delivery.restaurant.application.api.UpdateMenuItemCommand;
import com.delivery.restaurant.application.api.RestaurantManagementAccessUseCase;
import com.delivery.restaurant.domain.catalog.MenuItemStatus;
import com.delivery.restaurant.domain.ownership.ManagementAccessException;
import com.delivery.restaurant.domain.ownership.ManagementAccessFailure;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import com.delivery.restaurant.domain.ownership.RestaurantManagementAccessDecision;
import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.repository.MenuItemRepository;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class JpaMenuItemAdapterTest {

    @Mock MenuItemRepository menuItems;
    @Mock RestaurantRepository restaurants;
    @Mock SearchSyncPublisher search;
    @Mock RestaurantManagementAccessUseCase access;

    private Restaurant restaurant;
    private MenuItem item;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(42L);
        restaurant.setCreatorId(700L);
        restaurant.setOwnerPrincipalId(7L);
        item = new MenuItem();
        item.setId(9L);
        item.setRestaurant(restaurant);
        item.setName("Old");
        item.setDescription("Original");
        item.setPrice(new BigDecimal("50000.00"));
        item.setStatus(MenuItem.Status.AVAILABLE);
        item.setImage("old.png");
        item.setVersion(3L);
    }

    @Test
    void createLoadsParentAppliesPlanAndWritesCreateOutbox() {
        when(restaurants.findById(42L)).thenReturn(Optional.of(restaurant));
        when(menuItems.save(any(MenuItem.class))).thenAnswer(invocation -> {
            MenuItem saved = invocation.getArgument(0);
            saved.setId(10L);
            saved.setVersion(0L);
            return saved;
        });

        MenuItemCreateResultHolder result = new MenuItemCreateResultHolder();
        var adapter = new JpaMenuItemCreationAdapter(menuItems, restaurants, search);
        var created = adapter.create(createCommand(), facts -> {
            assertThat(facts.ownerPrincipalId()).isEqualTo(7L);
            assertThat(facts.creatorId()).isEqualTo(700L);
            return new MenuItemMutationPlan(null, 42L, 7L, "Pho", "Classic",
                    new BigDecimal("55000.00"), MenuItemStatus.AVAILABLE, "pho.png");
        });
        result.value = created.orElseThrow();

        assertThat(result.value.snapshot()).extracting(MenuItemSnapshot::id).isEqualTo(10L);
        assertThat(result.value.snapshot().price()).isEqualByComparingTo("55000.00");
        assertThat(result.value.snapshot().status()).isEqualTo(MenuItemStatus.AVAILABLE);
        ArgumentCaptor<MenuItem> saved = ArgumentCaptor.forClass(MenuItem.class);
        verify(menuItems).save(saved.capture());
        assertThat(saved.getValue().getRestaurant()).isSameAs(restaurant);
        assertThat(saved.getValue().getStatus()).isEqualTo(MenuItem.Status.AVAILABLE);
        verify(search).publishDishChange(saved.getValue(), "CREATE");
    }

    @Test
    void createReportsLegacyOwnershipClaimBeforeParentMutation() {
        restaurant.setOwnerPrincipalId(null);
        when(restaurants.findById(42L)).thenReturn(Optional.of(restaurant));
        when(menuItems.save(any(MenuItem.class))).thenAnswer(invocation -> {
            MenuItem saved = invocation.getArgument(0);
            saved.setId(10L);
            saved.setVersion(0L);
            return saved;
        });

        var adapter = new JpaMenuItemCreationAdapter(menuItems, restaurants, search);
        var created = adapter.create(createCommand(), facts -> new MenuItemMutationPlan(
                null, 42L, 7L, "Pho", "Classic", new BigDecimal("55000.00"),
                MenuItemStatus.AVAILABLE, "pho.png"));

        assertThat(created.orElseThrow().claimedLegacyOwnership()).isTrue();
        assertThat(restaurant.getOwnerPrincipalId()).isEqualTo(7L);
    }

    @Test
    void createMissingParentReturnsEmptyWithoutMutationOrSideEffects() {
        when(restaurants.findById(404L)).thenReturn(Optional.empty());

        var adapter = new JpaMenuItemCreationAdapter(menuItems, restaurants, search);
        assertThat(adapter.create(createCommand(404L), facts -> {
            throw new AssertionError("decision must not run");
        })).isEmpty();

        verifyNoInteractions(menuItems, search);
    }

    @Test
    void updateLocksItemMergesPlanFlushesWithoutReparentingAndWritesUpdateOutbox() {
        when(menuItems.findById(9L)).thenReturn(Optional.of(item));
        when(menuItems.saveAndFlush(item)).thenReturn(item);
        var adapter = new JpaMenuItemUpdateAdapter(menuItems, search);
        Optional<com.delivery.restaurant.application.api.MenuItemUpdateResult> updated = adapter.update(
                new UpdateMenuItemCommand(9L, 7L, 700L, RestaurantActorRole.SHOP_OWNER,
                        true, "New", "Updated", new BigDecimal("65000.00"),
                        MenuItemStatus.SOLD_OUT, "new.png", 999L),
                facts -> {
                    assertThat(facts.restaurantId()).isEqualTo(42L);
                    return new MenuItemMutationPlan(9L, 42L, 7L, "New", "Updated",
                            new BigDecimal("65000.00"), MenuItemStatus.SOLD_OUT, "new.png");
                });

        MenuItemUpdateResult result = updated.orElseThrow();
        assertThat(result.snapshot().restaurantId()).isEqualTo(42L);
        assertThat(result.snapshot().price()).isEqualByComparingTo("65000.00");
        assertThat(result.snapshot().status()).isEqualTo(MenuItemStatus.SOLD_OUT);
        assertThat(item.getRestaurant()).isSameAs(restaurant);
        assertThat(item.getName()).isEqualTo("New");
        verify(menuItems).saveAndFlush(item);
        verify(search).publishDishChange(item, "UPDATE");
    }

    @Test
    void updateMissingItemReturnsEmptyWithoutDecisionOrMutation() {
        when(menuItems.findById(404L)).thenReturn(Optional.empty());
        var adapter = new JpaMenuItemUpdateAdapter(menuItems, search);

        assertThat(adapter.update(
                new UpdateMenuItemCommand(404L, 7L, 700L, RestaurantActorRole.SHOP_OWNER,
                        true, null, null, null, null, null, null), facts -> {
                    throw new AssertionError("decision must not run");
                })).isEmpty();
        verify(menuItems, never()).saveAndFlush(any());
        verifyNoInteractions(search);
    }

    @Test
    void readAdapterUsesPublicAvailabilityProjectionAndMapsPageMetadata() {
        when(menuItems.findByRestaurantIdAndStatusAndRestaurantLifecycleStatusNot(
                eq(42L), eq(MenuItem.Status.AVAILABLE),
                eq(com.delivery.restaurant.domain.catalog.RestaurantStatus.ARCHIVED), any()))
                .thenReturn(List.of(item));
        when(menuItems.findPageByRestaurantIdAndStatusAndRestaurantLifecycleStatusNot(
                eq(42L), eq(MenuItem.Status.AVAILABLE),
                eq(com.delivery.restaurant.domain.catalog.RestaurantStatus.ARCHIVED), any()))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(1, 24), 25));

        var adapter = new JpaMenuItemReadAdapter(menuItems, restaurants, access);
        assertThat(adapter.findPublicByRestaurant(42L, 100)).extracting(MenuItemSnapshot::id)
                .containsExactly(9L);
        MenuItemPageSlice page = adapter.pagePublicByRestaurant(42L, 1, 24);
        assertThat(page.items()).extracting(MenuItemSnapshot::id).containsExactly(9L);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(24);
        assertThat(page.totalItems()).isEqualTo(25);
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    void managementSpecificParentDistinguishesMissingAndForeignOwner() {
        var adapter = new JpaMenuItemReadAdapter(menuItems, restaurants, access);
        when(restaurants.findById(404L)).thenReturn(Optional.empty());
        assertThat(adapter.findManaged(query(404L, RestaurantActorRole.SHOP_OWNER, true))).isEmpty();

        when(restaurants.findById(42L)).thenReturn(Optional.of(restaurant));
        when(access.resolve(any(), eq(101L), eq(700L), eq(RestaurantActorRole.SHOP_OWNER), eq(true)))
                .thenThrow(new ManagementAccessException(ManagementAccessFailure.ACTOR_DOES_NOT_OWN_RESTAURANT));
        assertThatThrownBy(() -> adapter.findManaged(query(42L, RestaurantActorRole.SHOP_OWNER, true)))
                .isInstanceOf(ManagementAccessException.class);
        verify(menuItems, never()).findPageByRestaurantId(eq(42L), any());
    }

    @Test
    void managementReadsUseAllRowsForAdminAndPrincipalFirstLegacyFallbackForOwner() {
        when(menuItems.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 100), 1));
        when(menuItems.findManagedByOwner(eq(101L), eq(700L), eq(true), any()))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 24), 1));
        var adapter = new JpaMenuItemReadAdapter(menuItems, restaurants, access);

        assertThat(adapter.findManaged(query(null, RestaurantActorRole.ADMIN, true))).containsInstanceOf(MenuItemPageSlice.class);
        adapter.findManaged(query(null, RestaurantActorRole.SHOP_OWNER, false));
        verify(menuItems).findManagedByOwner(101L, 700L, true, PageRequest.of(0, 24));
    }

    private CreateMenuItemCommand createCommand() {
        return createCommand(42L);
    }

    private CreateMenuItemCommand createCommand(Long restaurantId) {
        return new CreateMenuItemCommand(restaurantId, 7L, 700L, RestaurantActorRole.SHOP_OWNER,
                true, "Pho", "Classic", new BigDecimal("55000.00"), "pho.png");
    }

    private MenuItemManagementQuery query(Long restaurantId, RestaurantActorRole role, boolean enforced) {
        return new MenuItemManagementQuery(restaurantId, 101L, 700L, role, enforced, 0, 24);
    }

    private static final class MenuItemCreateResultHolder {
        private com.delivery.restaurant.application.api.MenuItemCreateResult value;
    }
}
