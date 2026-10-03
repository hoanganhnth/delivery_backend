package com.delivery.tracking.application;

import com.delivery.tracking.application.api.ShipperIdentityReadPort;
import com.delivery.tracking.application.api.ShipperIdentityResolution;
import com.delivery.tracking.application.api.ShipperIdentityUseCase;
import com.delivery.tracking.domain.TrackingAccessDeniedException;
import java.util.Objects;

/** Derives shipper identity from the local projection and the existing rollout gate. */
public final class DefaultShipperIdentityUseCase implements ShipperIdentityUseCase {
    private final ShipperIdentityReadPort identities;

    public DefaultShipperIdentityUseCase(ShipperIdentityReadPort identities) {
        this.identities = Objects.requireNonNull(identities, "identities");
    }

    @Override
    public ShipperIdentityResolution resolve(Long principalId, Long legacyUserId, boolean enforced) {
        if (principalId == null || principalId <= 0 || legacyUserId == null || legacyUserId <= 0) {
            throw new TrackingAccessDeniedException("Missing authenticated shipper identity");
        }
        var mapping = identities.findByPrincipalId(principalId);
        if (mapping.isPresent()) {
            if (!legacyUserId.equals(mapping.get().legacyUserId())) {
                throw new TrackingAccessDeniedException("Shipper identity projection is divergent");
            }
            return new ShipperIdentityResolution(mapping.get().shipperId(), false);
        }
        if (!enforced) {
            return new ShipperIdentityResolution(legacyUserId, true);
        }
        throw new TrackingAccessDeniedException("Shipper identity projection is not ready");
    }
}
