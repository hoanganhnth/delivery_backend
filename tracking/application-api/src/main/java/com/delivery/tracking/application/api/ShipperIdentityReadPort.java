package com.delivery.tracking.application.api;

import java.util.Optional;

public interface ShipperIdentityReadPort {
    Optional<ShipperIdentityFacts> findByPrincipalId(Long principalId);
}
