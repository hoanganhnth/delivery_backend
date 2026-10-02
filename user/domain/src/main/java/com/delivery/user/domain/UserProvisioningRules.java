package com.delivery.user.domain;

import java.util.Objects;

/** Immutable identity binding required by signed Auth provisioning. */
public final class UserProvisioningRules {
    private UserProvisioningRules() {}

    public static void validate(Long authId, Long principalId, String email, String role) {
        if (authId == null || principalId == null || email == null || email.isBlank()
                || role == null || role.isBlank()) {
            throw new InvalidProvisioningIdentity(
                    "authId, principalId, email and role are required for user provisioning");
        }
        if (!authId.equals(principalId)) {
            throw new InvalidProvisioningIdentity(
                    "authId and principalId must identify the same Auth account");
        }
    }

    public static void requireSameIdentity(Long authId, Long principalId, String email, String role,
            Long existingAuthId, Long existingPrincipalId, String existingEmail, String existingRole) {
        if (!Objects.equals(existingAuthId, authId)
                || !Objects.equals(existingPrincipalId, principalId)
                || !existingEmail.equalsIgnoreCase(email) || !existingRole.equals(role)) {
            throw new ProvisioningIdentityConflict(
                    "principalId is already linked to a different user identity");
        }
    }

    public static void requireEmailOwner(Long principalId, Long existingPrincipalId) {
        if (!principalId.equals(existingPrincipalId)) {
            throw new ProvisioningIdentityConflict(
                    "email is already linked to a different auth identity");
        }
    }
}
