package com.delivery.user.domain;

public final class InvalidProvisioningIdentity extends RuntimeException {
    public InvalidProvisioningIdentity(String message) {
        super(message);
    }
}
