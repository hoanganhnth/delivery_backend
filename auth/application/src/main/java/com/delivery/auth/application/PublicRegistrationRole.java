package com.delivery.auth.application;

import com.delivery.auth.domain.model.AuthAccount;
import com.delivery.auth.domain.policy.RegistrationPolicy;
import com.delivery.auth.domain.policy.RegistrationPolicyException;

final class PublicRegistrationRole {
    private PublicRegistrationRole() {}
    static AuthAccount.Role parse(String role) {
        if (role == null || role.isBlank()) throw new IllegalArgumentException("Role is required");
        // Preserve production admission: whitespace is not accepted as a role alias.
        if (!role.equals(role.trim())) throw new IllegalArgumentException("Invalid role: " + role);
        try {
            return RegistrationPolicy.parsePublicRole(role);
        } catch (RegistrationPolicyException failure) {
            if (failure.violation() == com.delivery.auth.domain.policy.RegistrationRuleViolation.ROLE_NOT_PUBLIC) {
                if ("ADMIN".equalsIgnoreCase(role)) {
                    throw new IllegalArgumentException("ADMIN accounts cannot be self-registered");
                }
                throw new IllegalArgumentException("SHIPPER accounts require operator provisioning and profile onboarding");
            }
            throw new IllegalArgumentException("Invalid role: " + role);
        }
    }

}
