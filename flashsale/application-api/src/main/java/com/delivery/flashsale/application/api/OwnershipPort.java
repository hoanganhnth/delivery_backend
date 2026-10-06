package com.delivery.flashsale.application.api;

public interface OwnershipPort {
    void requireOwnedBy(Long restaurantId, Long principalId, Long legacyId);
}
