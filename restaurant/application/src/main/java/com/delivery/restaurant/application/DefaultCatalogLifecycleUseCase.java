package com.delivery.restaurant.application;

import com.delivery.restaurant.application.api.*;
import com.delivery.restaurant.application.api.CatalogLifecycleEffectsPort.Audit;
import com.delivery.restaurant.domain.catalog.*;
import com.delivery.restaurant.domain.ownership.*;
import java.util.Objects;

/** Authorizes and applies catalog transitions with version admission and atomic effects. */
public final class DefaultCatalogLifecycleUseCase implements CatalogLifecycleUseCase {
    private final CatalogLifecycleStorePort store;
    private final CatalogLifecycleEffectsPort effects;
    private final CatalogLifecycleDecisionUseCase decisions;
    private final RestaurantManagementAccessUseCase access;
    private final RestaurantTransactionPort transactions;
    private final boolean ownershipEnforced;

    public DefaultCatalogLifecycleUseCase(CatalogLifecycleStorePort store, CatalogLifecycleEffectsPort effects,
            CatalogLifecycleDecisionUseCase decisions, RestaurantManagementAccessUseCase access,
            RestaurantTransactionPort transactions, boolean ownershipEnforced) {
        this.store = Objects.requireNonNull(store); this.effects = Objects.requireNonNull(effects);
        this.decisions = Objects.requireNonNull(decisions); this.access = Objects.requireNonNull(access);
        this.transactions = Objects.requireNonNull(transactions); this.ownershipEnforced = ownershipEnforced;
    }

    @Override public RestaurantSnapshot changeRestaurant(Long id, RestaurantStatus target, Long expectedVersion,
            Long principalId, Long legacyUserId, String role) {
        return transactions.required(() -> {
            var current = store.findRestaurant(id).orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
            authorize(new RestaurantManagementFacts(current.ownerPrincipalId(), current.creatorId()), principalId, legacyUserId, role);
            requireExpectedVersion(current.version(), expectedVersion);
            var after = decisions.decideRestaurant(current.lifecycleStatus(), target, catalogRole(role));
            if (current.lifecycleStatus() == after) return current;
            var saved = store.saveRestaurantStatus(id, after);
            effects.recordAudit(new Audit("RESTAURANT", id, after == RestaurantStatus.ARCHIVED ? "ARCHIVE" : "RESTORE_OR_UPDATE",
                    principalId, role, current.lifecycleStatus().name(), after.name(), version(current.version()), version(saved.version())));
            effects.publishRestaurant(id, after == RestaurantStatus.ARCHIVED ? "DELETE" : "UPDATE");
            return saved;
        });
    }

    @Override public MenuItemSnapshot changeMenuItem(Long id, MenuItemStatus target, Long expectedVersion,
            Long principalId, Long legacyUserId, String role) {
        return transactions.required(() -> {
            var facts = store.findMenuItem(id).orElseThrow(() -> new ResourceNotFoundException("MenuItem not found"));
            authorize(facts.ownership(), principalId, legacyUserId, role);
            var current = facts.snapshot();
            requireExpectedVersion(current.version(), expectedVersion);
            var after = decisions.decideMenuItem(current.status(), target, catalogRole(role));
            if (current.status() == after) return current;
            var saved = store.saveMenuItemStatus(id, after);
            effects.recordAudit(new Audit("MENU_ITEM", id, after == MenuItemStatus.ARCHIVED ? "ARCHIVE" : "RESTORE_OR_UPDATE",
                    principalId, role, current.status().name(), after.name(), version(current.version()), version(saved.version())));
            effects.publishMenuItem(id, after == MenuItemStatus.ARCHIVED ? "DELETE" : "UPDATE");
            return saved;
        });
    }

    private void authorize(RestaurantManagementFacts facts, Long principalId, Long legacyUserId, String role) {
        try {
            var decision = access.resolve(facts, principalId, legacyUserId, actorRole(role), ownershipEnforced);
            if (decision.usedLegacyFallback()) effects.legacyOwnershipFallback();
        } catch (ManagementAccessException | IllegalArgumentException denied) {
            throw new CatalogAccessDeniedException("Actor does not own this restaurant");
        }
    }
    private void requireExpectedVersion(Long actual, Long expected) {
        if (expected == null) { effects.missingExpectedVersion(); return; }
        if (actual == null || !actual.equals(expected)) throw new StaleVersionException();
    }
    private static long version(Long value) { return value == null ? 0L : value; }
    private static RestaurantActorRole actorRole(String role) {
        if ("ADMIN".equalsIgnoreCase(role)) return RestaurantActorRole.ADMIN;
        if ("SHOP_OWNER".equalsIgnoreCase(role)) return RestaurantActorRole.SHOP_OWNER;
        return RestaurantActorRole.OTHER;
    }
    private static CatalogActorRole catalogRole(String role) {
        if ("ADMIN".equalsIgnoreCase(role)) return CatalogActorRole.ADMIN;
        if ("SHOP_OWNER".equalsIgnoreCase(role)) return CatalogActorRole.SHOP_OWNER;
        return null;
    }
}
