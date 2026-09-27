package com.delivery.restaurant_service.service.impl;

import com.delivery.observability.CorrelationContext;
import com.delivery.restaurant.application.DefaultCatalogLifecycleDecisionUseCase;
import com.delivery.restaurant.application.api.CatalogLifecycleDecisionUseCase;
import com.delivery.restaurant.domain.catalog.CatalogActorRole;
import com.delivery.restaurant.domain.catalog.CatalogDomainException;
import com.delivery.restaurant.domain.catalog.MenuItemLifecyclePolicy;
import com.delivery.restaurant.domain.catalog.MenuItemStatus;
import com.delivery.restaurant.domain.catalog.RestaurantLifecyclePolicy;
import com.delivery.restaurant.domain.catalog.RestaurantStatus;
import com.delivery.restaurant_service.common.constants.RoleConstants;
import com.delivery.restaurant_service.dto.request.MenuItemLifecycleRequest;
import com.delivery.restaurant_service.dto.request.RestaurantLifecycleRequest;
import com.delivery.restaurant_service.dto.response.MenuItemResponse;
import com.delivery.restaurant_service.dto.response.RestaurantResponse;
import com.delivery.restaurant_service.entity.CatalogLifecycleAudit;
import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.exception.ResourceNotFoundException;
import com.delivery.restaurant_service.exception.StaleVersionException;
import com.delivery.restaurant_service.mapper.MenuItemMapper;
import com.delivery.restaurant_service.mapper.RestaurantMapper;
import com.delivery.restaurant_service.repository.CatalogLifecycleAuditRepository;
import com.delivery.restaurant_service.repository.MenuItemRepository;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import com.delivery.restaurant_service.service.SearchSyncPublisher;
import com.delivery.restaurant_service.service.ownership.RestaurantOwnershipPolicy;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.OptimisticLockException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogLifecycleService {
    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;
    private final RestaurantMapper restaurantMapper;
    private final MenuItemMapper menuItemMapper;
    private final SearchSyncPublisher searchSyncPublisher;
    private final RestaurantOwnershipPolicy ownershipPolicy;
    private final CatalogLifecycleDecisionUseCase lifecycleDecisionUseCase;
    private final CatalogLifecycleAuditRepository auditRepository;
    private final MeterRegistry meterRegistry;

    @Autowired
    public CatalogLifecycleService(
            RestaurantRepository restaurantRepository,
            MenuItemRepository menuItemRepository,
            RestaurantMapper restaurantMapper,
            MenuItemMapper menuItemMapper,
            SearchSyncPublisher searchSyncPublisher,
            RestaurantOwnershipPolicy ownershipPolicy,
            CatalogLifecycleDecisionUseCase lifecycleDecisionUseCase,
            CatalogLifecycleAuditRepository auditRepository,
            MeterRegistry meterRegistry) {
        this.restaurantRepository = restaurantRepository;
        this.menuItemRepository = menuItemRepository;
        this.restaurantMapper = restaurantMapper;
        this.menuItemMapper = menuItemMapper;
        this.searchSyncPublisher = searchSyncPublisher;
        this.ownershipPolicy = ownershipPolicy;
        this.lifecycleDecisionUseCase = lifecycleDecisionUseCase;
        this.auditRepository = auditRepository;
        this.meterRegistry = meterRegistry;
    }

    /** Transitional constructor retained for host unit tests during application wiring migration. */
    public CatalogLifecycleService(
            RestaurantRepository restaurantRepository,
            MenuItemRepository menuItemRepository,
            RestaurantMapper restaurantMapper,
            MenuItemMapper menuItemMapper,
            SearchSyncPublisher searchSyncPublisher,
            RestaurantOwnershipPolicy ownershipPolicy,
            RestaurantLifecyclePolicy restaurantPolicy,
            MenuItemLifecyclePolicy menuItemPolicy,
            CatalogLifecycleAuditRepository auditRepository,
            MeterRegistry meterRegistry) {
        this(restaurantRepository, menuItemRepository, restaurantMapper, menuItemMapper,
                searchSyncPublisher, ownershipPolicy,
                new DefaultCatalogLifecycleDecisionUseCase(restaurantPolicy, menuItemPolicy),
                auditRepository, meterRegistry);
    }

    @Transactional
    public RestaurantResponse changeRestaurantLifecycle(Long id, RestaurantLifecycleRequest request,
            Long principalId, Long legacyUserId, String role) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
        ownershipPolicy.assertCanManage(restaurant, principalId, legacyUserId, role);
        requireExpectedVersion(restaurant.getVersion(), request.expectedVersion());
        RestaurantStatus before = restaurant.getLifecycleStatus();
        RestaurantStatus after = transitionRestaurant(before, request.targetStatus(), role);
        if (before == after) return restaurantMapper.toResponse(restaurant);
        long beforeVersion = versionOrZero(restaurant.getVersion());
        restaurant.setLifecycleStatus(after);
        try {
            Restaurant saved = restaurantRepository.saveAndFlush(restaurant);
            recordAudit("RESTAURANT", id, action(before, after), principalId, role,
                    before.name(), after.name(), beforeVersion, versionOrZero(saved.getVersion()));
            if (after == RestaurantStatus.ARCHIVED) {
                searchSyncPublisher.publishRestaurantChange(saved, "DELETE");
            } else {
                searchSyncPublisher.publishRestaurantChange(saved, "UPDATE");
            }
            return restaurantMapper.toResponse(saved);
        } catch (OptimisticLockException | ObjectOptimisticLockingFailureException ex) {
            throw new StaleVersionException(ex);
        }
    }

    @Transactional
    public MenuItemResponse changeMenuItemLifecycle(Long id, MenuItemLifecycleRequest request,
            Long principalId, Long legacyUserId, String role) {
        MenuItem item = menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem not found"));
        ownershipPolicy.assertCanManage(item.getRestaurant(), principalId, legacyUserId, role);
        requireExpectedVersion(item.getVersion(), request.expectedVersion());
        MenuItemStatus before = MenuItemStatus.valueOf(item.getStatus().name());
        MenuItemStatus after = transitionMenuItem(before, request.targetStatus(), role);
        if (before == after) return menuItemMapper.toResponse(item);
        long beforeVersion = versionOrZero(item.getVersion());
        item.setStatus(MenuItem.Status.valueOf(after.name()));
        try {
            MenuItem saved = menuItemRepository.saveAndFlush(item);
            recordAudit("MENU_ITEM", id, action(before, after), principalId, role,
                    before.name(), after.name(), beforeVersion, versionOrZero(saved.getVersion()));
            if (after == MenuItemStatus.ARCHIVED) {
                searchSyncPublisher.publishDishChange(saved, "DELETE");
            } else {
                searchSyncPublisher.publishDishChange(saved, "UPDATE");
            }
            return menuItemMapper.toResponse(saved);
        } catch (OptimisticLockException | ObjectOptimisticLockingFailureException ex) {
            throw new StaleVersionException(ex);
        }
    }

    public void archiveRestaurant(Long id, Long principalId, Long legacyUserId, String role) {
        changeRestaurantLifecycle(id,
                new RestaurantLifecycleRequest(RestaurantStatus.ARCHIVED, null),
                principalId, legacyUserId, role);
    }

    public void archiveMenuItem(Long id, Long principalId, Long legacyUserId, String role) {
        changeMenuItemLifecycle(id,
                new MenuItemLifecycleRequest(MenuItemStatus.ARCHIVED, null),
                principalId, legacyUserId, role);
    }

    public double missingExpectedVersionCount() {
        return meterRegistry.get("delivery.catalog.expected_version.missing").counter().count();
    }

    private RestaurantStatus transitionRestaurant(RestaurantStatus before, RestaurantStatus target, String role) {
        try {
            return lifecycleDecisionUseCase.decideRestaurant(before, target, actorRole(role));
        } catch (CatalogDomainException ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    private MenuItemStatus transitionMenuItem(MenuItemStatus before, MenuItemStatus target, String role) {
        try {
            return lifecycleDecisionUseCase.decideMenuItem(before, target, actorRole(role));
        } catch (CatalogDomainException ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    private CatalogActorRole actorRole(String role) {
        if (RoleConstants.ADMIN.equalsIgnoreCase(role)) return CatalogActorRole.ADMIN;
        if (RoleConstants.OWNER.equalsIgnoreCase(role)) return CatalogActorRole.SHOP_OWNER;
        return null;
    }

    private void requireExpectedVersion(Long actual, Long expected) {
        if (expected == null) {
            Counter.builder("delivery.catalog.expected_version.missing")
                    .register(meterRegistry).increment();
            return;
        }
        if (actual == null || !actual.equals(expected)) throw new StaleVersionException();
    }

    private void recordAudit(String type, Long id, String action, Long principalId, String role,
            String before, String after, long beforeVersion, long afterVersion) {
        CatalogLifecycleAudit audit = new CatalogLifecycleAudit();
        audit.setAggregateType(type);
        audit.setAggregateId(id);
        audit.setAction(action);
        audit.setActorPrincipalId(principalId);
        audit.setActorRole(role);
        audit.setBeforeStatus(before);
        audit.setAfterStatus(after);
        audit.setBeforeVersion(beforeVersion);
        audit.setAfterVersion(afterVersion);
        audit.setCorrelationId(CorrelationContext.currentOrCreate());
        audit.setOccurredAt(java.time.LocalDateTime.now());
        auditRepository.save(audit);
    }

    private String action(Object before, Object after) {
        return "ARCHIVED".equals(after.toString()) ? "ARCHIVE" : "RESTORE_OR_UPDATE";
    }

    private long versionOrZero(Long version) {
        return version == null ? 0L : version;
    }
}
