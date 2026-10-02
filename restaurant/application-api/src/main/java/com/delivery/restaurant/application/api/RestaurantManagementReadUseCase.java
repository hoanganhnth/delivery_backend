package com.delivery.restaurant.application.api;

public interface RestaurantManagementReadUseCase {
    RestaurantManagementResult readForAdmin(Long principalId, Long legacyUserId,
            boolean principalOwnershipEnforced);

    RestaurantManagementResult readForOwner(Long principalId, Long legacyUserId,
            boolean principalOwnershipEnforced);
}
