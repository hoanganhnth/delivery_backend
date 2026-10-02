package com.delivery.restaurant_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.restaurant.domain.catalog.MenuItemStatus;
import com.delivery.restaurant.domain.catalog.RestaurantStatus;
import com.delivery.restaurant.application.DefaultCatalogLifecycleDecisionUseCase;
import com.delivery.restaurant.application.DefaultRestaurantManagementAccessUseCase;
import com.delivery.restaurant_service.dto.request.MenuItemLifecycleRequest;
import com.delivery.restaurant_service.dto.request.RestaurantLifecycleRequest;
import com.delivery.restaurant_service.dto.response.MenuItemResponse;
import com.delivery.restaurant_service.dto.response.RestaurantResponse;
import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.entity.CatalogLifecycleAudit;
import com.delivery.restaurant.domain.catalog.StaleVersionException;
import com.delivery.restaurant_service.mapper.MenuItemMapper;
import com.delivery.restaurant_service.mapper.RestaurantMapper;
import com.delivery.restaurant_service.repository.MenuItemRepository;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import com.delivery.restaurant_service.repository.CatalogLifecycleAuditRepository;
import com.delivery.restaurant.application.DefaultCatalogLifecycleUseCase;
import com.delivery.restaurant.application.api.*;
import com.delivery.restaurant_service.service.JpaCatalogLifecycleAdapter;
import com.delivery.restaurant_service.service.ownership.RestaurantOwnershipPolicy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogLifecycleServiceTest {

    @Mock RestaurantRepository restaurantRepository;
    @Mock MenuItemRepository menuItemRepository;
    @Mock RestaurantMapper restaurantMapper;
    @Mock MenuItemMapper menuItemMapper;
    @Mock SearchSyncPublisher searchSyncPublisher;
    @Mock CatalogLifecycleAuditRepository auditRepository;

    private LifecycleHarness service;
    private Restaurant restaurant;
    private MenuItem item;

    @BeforeEach
    void setUp() {
        service = new LifecycleHarness();
        restaurant = new Restaurant();
        restaurant.setId(10L);
        restaurant.setOwnerPrincipalId(7L);
        restaurant.setLifecycleStatus(RestaurantStatus.ACTIVE);
        restaurant.setVersion(4L);
        item = new MenuItem();
        item.setId(20L);
        item.setRestaurant(restaurant);
        item.setStatus(MenuItem.Status.AVAILABLE);
        item.setVersion(2L);
    }

    @Test
    void ownerMayPauseRestaurantAndAuditStoresWhitelistedTransition() {
        when(restaurantRepository.findById(10L)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.saveAndFlush(restaurant)).thenReturn(restaurant);
        RestaurantResponse response = new RestaurantResponse();
        when(restaurantMapper.toResponse(any(RestaurantSnapshot.class))).thenReturn(response);

        RestaurantResponse result = service.changeRestaurantLifecycle(
                10L, new RestaurantLifecycleRequest(RestaurantStatus.PAUSED, 4L),
                7L, 700L, "SHOP_OWNER");

        assertThat(result).isSameAs(response);
        assertThat(restaurant.getLifecycleStatus()).isEqualTo(RestaurantStatus.PAUSED);
        verify(auditRepository).save(any());
        verify(searchSyncPublisher).publishRestaurantChange(restaurant, "UPDATE");
    }

    @Test
    void staleRestaurantVersionFailsBeforeAnyMutation() {
        when(restaurantRepository.findById(10L)).thenReturn(Optional.of(restaurant));

        assertThatThrownBy(() -> service.changeRestaurantLifecycle(
                10L, new RestaurantLifecycleRequest(RestaurantStatus.PAUSED, 3L),
                7L, 700L, "SHOP_OWNER"))
                .isInstanceOf(StaleVersionException.class)
                .hasMessage("STALE_VERSION");

        assertThat(restaurant.getLifecycleStatus()).isEqualTo(RestaurantStatus.ACTIVE);
        verify(restaurantRepository, never()).save(any());
        verifyNoAuditOrSideEffects();
    }

    @Test
    void missingExpectedVersionIsCompatibleButIncrementsMetric() {
        when(restaurantRepository.findById(10L)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.saveAndFlush(restaurant)).thenReturn(restaurant);
        when(restaurantMapper.toResponse(any(RestaurantSnapshot.class))).thenReturn(new RestaurantResponse());

        service.changeRestaurantLifecycle(
                10L, new RestaurantLifecycleRequest(RestaurantStatus.PAUSED, null),
                7L, 700L, "SHOP_OWNER");

        assertThat(service.missingExpectedVersionCount()).isEqualTo(1.0);
    }

    @Test
    void onlyAdminMayRestoreArchivedMenuItem() {
        item.setStatus(MenuItem.Status.ARCHIVED);
        when(menuItemRepository.findById(20L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.changeMenuItemLifecycle(
                20L, new MenuItemLifecycleRequest(MenuItemStatus.DISCONTINUED, 2L),
                7L, 700L, "SHOP_OWNER"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(menuItemRepository, never()).save(any());
    }

    @Test
    void restaurantArchiveKeepsMenuStateIndependentAndUsesDeleteProjection() {
        when(restaurantRepository.findById(10L)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.saveAndFlush(restaurant)).thenReturn(restaurant);

        service.changeRestaurantLifecycle(
                10L, new RestaurantLifecycleRequest(RestaurantStatus.ARCHIVED, 4L),
                7L, 700L, "SHOP_OWNER");

        assertThat(restaurant.getLifecycleStatus()).isEqualTo(RestaurantStatus.ARCHIVED);
        verify(searchSyncPublisher).publishRestaurantChange(restaurant, "DELETE");
    }

    @Test
    void idempotentTargetReturnsCurrentStateWithoutWritingAuditOrSideEffects() {
        restaurant.setLifecycleStatus(RestaurantStatus.PAUSED);
        when(restaurantRepository.findById(10L)).thenReturn(Optional.of(restaurant));
        RestaurantResponse response = new RestaurantResponse();
        when(restaurantMapper.toResponse(any(RestaurantSnapshot.class))).thenReturn(response);

        assertThat(service.changeRestaurantLifecycle(
                10L, new RestaurantLifecycleRequest(RestaurantStatus.PAUSED, 4L),
                7L, 700L, "SHOP_OWNER")).isSameAs(response);

        verify(restaurantRepository, never()).saveAndFlush(any());
        verifyNoAuditOrSideEffects();
    }

    @Test
    void foreignOwnerCannotChangeLifecycleOrCreateSideEffects() {
        when(restaurantRepository.findById(10L)).thenReturn(Optional.of(restaurant));

        assertThatThrownBy(() -> service.changeRestaurantLifecycle(
                10L, new RestaurantLifecycleRequest(RestaurantStatus.PAUSED, 4L),
                8L, 700L, "SHOP_OWNER"))
                .isInstanceOf(com.delivery.restaurant.domain.catalog.CatalogAccessDeniedException.class);

        assertThat(restaurant.getLifecycleStatus()).isEqualTo(RestaurantStatus.ACTIVE);
        verify(restaurantRepository, never()).saveAndFlush(any());
        verifyNoAuditOrSideEffects();
    }

    @Test
    void auditCapturesOnlyWhitelistedRestaurantTransitionFacts() {
        when(restaurantRepository.findById(10L)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.saveAndFlush(restaurant)).thenAnswer(invocation -> {
            restaurant.setVersion(5L);
            return restaurant;
        });

        service.changeRestaurantLifecycle(
                10L, new RestaurantLifecycleRequest(RestaurantStatus.PAUSED, 4L),
                7L, 700L, "SHOP_OWNER");

        ArgumentCaptor<CatalogLifecycleAudit> audit = ArgumentCaptor.forClass(CatalogLifecycleAudit.class);
        verify(auditRepository).save(audit.capture());
        assertThat(audit.getValue()).satisfies(record -> {
            assertThat(record.getAggregateType()).isEqualTo("RESTAURANT");
            assertThat(record.getAggregateId()).isEqualTo(10L);
            assertThat(record.getActorPrincipalId()).isEqualTo(7L);
            assertThat(record.getActorRole()).isEqualTo("SHOP_OWNER");
            assertThat(record.getBeforeStatus()).isEqualTo("ACTIVE");
            assertThat(record.getAfterStatus()).isEqualTo("PAUSED");
            assertThat(record.getBeforeVersion()).isEqualTo(4L);
            assertThat(record.getAfterVersion()).isEqualTo(5L);
            assertThat(record.getCorrelationId()).isNotBlank();
            assertThat(record.getOccurredAt()).isNotNull();
        });
    }

    @Test
    void menuArchiveWritesAuditAndDeleteProjection() {
        when(menuItemRepository.findById(20L)).thenReturn(Optional.of(item));
        when(menuItemRepository.saveAndFlush(item)).thenReturn(item);

        service.changeMenuItemLifecycle(
                20L, new MenuItemLifecycleRequest(MenuItemStatus.ARCHIVED, 2L),
                7L, 700L, "SHOP_OWNER");

        ArgumentCaptor<CatalogLifecycleAudit> audit = ArgumentCaptor.forClass(CatalogLifecycleAudit.class);
        verify(auditRepository).save(audit.capture());
        assertThat(audit.getValue().getAggregateType()).isEqualTo("MENU_ITEM");
        assertThat(audit.getValue().getBeforeStatus()).isEqualTo("AVAILABLE");
        assertThat(audit.getValue().getAfterStatus()).isEqualTo("ARCHIVED");
        verify(searchSyncPublisher).publishDishChange(item, "DELETE");
    }

    private void verifyNoAuditOrSideEffects() {
        verify(auditRepository, never()).save(any());
        verify(searchSyncPublisher, never()).publishRestaurantChange(any(), any());
    }
    private class LifecycleHarness {
        private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        private final JpaCatalogLifecycleAdapter adapter = new JpaCatalogLifecycleAdapter(
                restaurantRepository, menuItemRepository, auditRepository, searchSyncPublisher, metrics);
        private final DefaultCatalogLifecycleUseCase core = new DefaultCatalogLifecycleUseCase(adapter, adapter,
                new DefaultCatalogLifecycleDecisionUseCase(new com.delivery.restaurant.domain.catalog.RestaurantLifecyclePolicy(),
                        new com.delivery.restaurant.domain.catalog.MenuItemLifecyclePolicy()),
                new DefaultRestaurantManagementAccessUseCase(), new RestaurantTransactionPort() {
                    public <T> T required(java.util.function.Supplier<T> operation) { return operation.get(); }
                    public <T> T repeatableRead(java.util.function.Supplier<T> operation) { return operation.get(); }
                    public <T> T readOnly(java.util.function.Supplier<T> operation) { return operation.get(); }
                }, false);
        RestaurantResponse changeRestaurantLifecycle(Long id, RestaurantLifecycleRequest request, Long principal, Long legacy, String role) {
            return restaurantMapper.toResponse(core.changeRestaurant(id, request.targetStatus(), request.expectedVersion(), principal, legacy, role));
        }
        MenuItemResponse changeMenuItemLifecycle(Long id, MenuItemLifecycleRequest request, Long principal, Long legacy, String role) {
            return menuItemMapper.toResponse(core.changeMenuItem(id, request.targetStatus(), request.expectedVersion(), principal, legacy, role));
        }
        double missingExpectedVersionCount() { return metrics.get("delivery.catalog.expected_version.missing").counter().count(); }
    }
}
