package com.delivery.user.application.api;

/** Locally verifies Auth's signed handoff; implementations never perform registration. */
public interface ProvisioningIdentityPort {
    ProvisioningIdentity verify(String provisioningToken);
}
