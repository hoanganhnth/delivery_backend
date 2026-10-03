package com.delivery.restaurant.application;

import com.delivery.restaurant.application.api.*;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import com.delivery.restaurant.domain.ownership.ManagementAccessException;
import java.util.Objects;

/** Existing internal rollout and order-decision ownership rules, independently of transport. */
public final class DefaultRestaurantOwnershipLookupUseCase implements RestaurantOwnershipLookupUseCase {
    private final RestaurantOwnershipReadPort ownership;
    private final RestaurantTransactionPort transactions;
    private final RestaurantManagementAccessUseCase access;
    public DefaultRestaurantOwnershipLookupUseCase(RestaurantOwnershipReadPort ownership, RestaurantTransactionPort transactions, RestaurantManagementAccessUseCase access) {
        this.ownership = Objects.requireNonNull(ownership);
        this.transactions = Objects.requireNonNull(transactions);
        this.access = Objects.requireNonNull(access);
    }
    @Override public RestaurantOwnershipLookupResult internalCheck(Long restaurant, Long owner, Long legacyOwner, boolean enforced) {
        return transactions.readOnly(() -> ownership.findOwnership(restaurant).map(facts -> {
            if (legacyOwner == null) return new RestaurantOwnershipLookupResult(owner != null && owner.equals(facts.creatorId()), false);
            if (owner != null && owner.equals(facts.ownerPrincipalId())) return new RestaurantOwnershipLookupResult(true, false);
            boolean fallback = !enforced && facts.ownerPrincipalId() == null && legacyOwner.equals(facts.creatorId());
            return new RestaurantOwnershipLookupResult(fallback, fallback);
        }).orElseGet(() -> new RestaurantOwnershipLookupResult(false, false)));
    }
    @Override public boolean canDecideOrder(Long restaurant, RestaurantActorRole role, Long principal, Long legacyUser, boolean enforced) {
        if (principal == null || principal <= 0) return false;
        if (role == RestaurantActorRole.ADMIN) return true;
        if (role != RestaurantActorRole.SHOP_OWNER) return false;
        return transactions.readOnly(() -> ownership.findOwnership(restaurant).map(facts -> {
            try {
                access.resolve(facts, principal, legacyUser, role, enforced);
                return true;
            } catch (ManagementAccessException denied) {
                return false;
            }
        }).orElse(false));
    }
}
