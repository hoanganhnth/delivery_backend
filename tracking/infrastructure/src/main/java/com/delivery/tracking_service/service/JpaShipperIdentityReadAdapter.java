package com.delivery.tracking_service.service;

import com.delivery.tracking.application.api.ShipperIdentityFacts;
import com.delivery.tracking.application.api.ShipperIdentityReadPort;
import com.delivery.tracking_service.repository.ShipperIdentityProjectionRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class JpaShipperIdentityReadAdapter implements ShipperIdentityReadPort {
    private final ShipperIdentityProjectionRepository projections;

    public JpaShipperIdentityReadAdapter(ShipperIdentityProjectionRepository projections) {
        this.projections = projections;
    }

    @Override
    public Optional<ShipperIdentityFacts> findByPrincipalId(Long principalId) {
        return projections.findById(principalId)
                .map(row -> new ShipperIdentityFacts(row.getLegacyUserId(), row.getShipperId()));
    }
}
