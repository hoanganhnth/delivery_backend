package com.delivery.identity.contracts;

public record IdentityPrincipal(
        Long principalId,
        IdentityRole role,
        IdentityLifecycleStatus lifecycleStatus) {
}
