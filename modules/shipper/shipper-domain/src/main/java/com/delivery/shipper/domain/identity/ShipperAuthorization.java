package com.delivery.shipper.domain.identity;

public final class ShipperAuthorization {
    private ShipperAuthorization() { }

    public static AuthorizationDecision readSelf(ShipperRole role, IdentityRef owner, long principalId, Long legacyUserId) {
        return role == ShipperRole.SHIPPER && owner.owns(principalId, legacyUserId) ? AuthorizationDecision.ALLOW : AuthorizationDecision.DENY;
    }

    public static AuthorizationDecision readById(ShipperRole role) {
        return role == ShipperRole.ADMIN ? AuthorizationDecision.ALLOW : AuthorizationDecision.DENY;
    }
}
