package com.delivery.tracking.application.api;

public interface ShipperIdentityUseCase {
    ShipperIdentityResolution resolve(Long principalId, Long legacyUserId, boolean enforced);
}
