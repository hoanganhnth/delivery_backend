package com.delivery.restaurant.domain.ownership;

public record PrincipalOwnershipFacts(long principalId, boolean shopOwner, boolean active) {

    public PrincipalOwnershipFacts {
        if (principalId <= 0) {
            throw new IllegalArgumentException("principalId must be positive");
        }
    }
}
