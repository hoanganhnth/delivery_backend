package com.delivery.shipper.domain.identity;

import java.util.Objects;

public record IdentityStatusProjection(long principalId, String status, long version) {
    public IdentityStatusProjection {
        if (principalId <= 0 || version < 0) throw new IllegalArgumentException("invalid identity projection");
        if (status == null || status.isBlank()) throw new IllegalArgumentException("status is required");
    }

    /** Newer versions win; equal versions are idempotent only for the same state. */
    public IdentityStatusProjection apply(String nextStatus, long nextVersion) {
        Objects.requireNonNull(nextStatus, "nextStatus");
        if (nextVersion < version) return this;
        if (nextVersion == version && !status.equals(nextStatus)) throw new IllegalArgumentException("identity version conflict");
        return nextVersion == version ? this : new IdentityStatusProjection(principalId, nextStatus, nextVersion);
    }
}
