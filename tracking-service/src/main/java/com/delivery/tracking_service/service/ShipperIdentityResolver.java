package com.delivery.tracking_service.service;

import com.delivery.tracking_service.repository.ShipperIdentityProjectionRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/** Resolves the authenticated principal without an HTTP call to shipper-service. */
@Service
public class ShipperIdentityResolver {
    private final ShipperIdentityProjectionRepository projections;
    private final boolean enforced;
    private final Counter preEnforcementFallback;
    public ShipperIdentityResolver(ShipperIdentityProjectionRepository projections,
            @Value("${app.shipper.identity-projection.enforced:false}") boolean enforced,
            MeterRegistry meterRegistry) {
        this.projections = projections; this.enforced = enforced;
        this.preEnforcementFallback = Counter.builder("delivery.identity.legacy.fallback")
                .tag("service", "tracking").tag("surface", "shipper_mapping_pre_enforcement")
                .register(meterRegistry);
    }
    public Long requireShipperId(Long principalId, Long legacyUserId) {
        if (principalId == null || principalId <= 0 || legacyUserId == null || legacyUserId <= 0) {
            throw new AccessDeniedException("Missing authenticated shipper identity");
        }
        if (!enforced) {
            preEnforcementFallback.increment();
            return legacyUserId;
        }
        return projections.findById(principalId)
                .filter(mapping -> legacyUserId.equals(mapping.getLegacyUserId()))
                .map(mapping -> mapping.getShipperId())
                .orElseThrow(() -> new AccessDeniedException("Shipper identity projection is not ready"));
    }
}
