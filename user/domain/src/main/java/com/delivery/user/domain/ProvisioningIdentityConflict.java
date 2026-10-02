package com.delivery.user.domain;

public final class ProvisioningIdentityConflict extends RuntimeException {
    public ProvisioningIdentityConflict(String message) {
        super(message);
    }
}
