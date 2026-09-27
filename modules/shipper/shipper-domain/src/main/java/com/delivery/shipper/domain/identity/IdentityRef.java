package com.delivery.shipper.domain.identity;

/** Stable principal is canonical; legacy userId is retained only for compatibility. */
public record IdentityRef(long principalId, Long legacyUserId) {
    public IdentityRef {
        requirePositive(principalId, "principalId");
        if (legacyUserId != null) requirePositive(legacyUserId, "legacyUserId");
    }

    public boolean owns(long candidatePrincipalId, Long candidateLegacyUserId) {
        if (candidatePrincipalId > 0 && candidatePrincipalId == principalId) return true;
        return candidatePrincipalId <= 0 && legacyUserId != null && legacyUserId.equals(candidateLegacyUserId);
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
    }
}
