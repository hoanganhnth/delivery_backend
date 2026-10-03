package com.delivery.tracking_service.service;

import com.delivery.tracking.application.api.ShipperIdentityUseCase;
import com.delivery.tracking.domain.TrackingAccessDeniedException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/** Maps core identity results/denials to transport security and rollout metrics. */
@Service
public class ShipperIdentityResolver {
    private final ShipperIdentityUseCase identities;
    private final boolean enforced;
    private final Counter preEnforcementFallback;
    public ShipperIdentityResolver(ShipperIdentityUseCase identities,
            @Value("${app.shipper.identity-projection.enforced:false}") boolean enforced,
            MeterRegistry meterRegistry) {
        this.identities = identities; this.enforced = enforced;
        this.preEnforcementFallback = Counter.builder("delivery.identity.legacy.fallback")
                .tag("service", "tracking").tag("surface", "shipper_mapping_pre_enforcement")
                .register(meterRegistry);
    }
    public Long requireShipperId(Long principalId, Long legacyUserId) {
        try {
            var resolution = identities.resolve(principalId, legacyUserId, enforced);
            if (resolution.usedLegacyFallback()) preEnforcementFallback.increment();
            return resolution.shipperId();
        } catch (TrackingAccessDeniedException denied) {
            throw new AccessDeniedException(denied.getMessage(), denied);
        }
    }
}
