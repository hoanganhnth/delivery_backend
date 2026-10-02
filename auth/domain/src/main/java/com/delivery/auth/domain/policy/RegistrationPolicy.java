package com.delivery.auth.domain.policy;

import com.delivery.auth.domain.model.AuthAccount;
import java.util.Locale;

/**
 * Auth-owned role admission rules. Public registration is intentionally
 * narrower than operator provisioning.
 */
public final class RegistrationPolicy {

    private RegistrationPolicy() {
    }

    public static AuthAccount.Role parsePublicRole(String rawRole) {
        if (rawRole == null || rawRole.isBlank()) {
            throw new RegistrationPolicyException(RegistrationRuleViolation.ROLE_REQUIRED);
        }

        String normalized = rawRole.trim().toUpperCase(Locale.ROOT);
        if ("CUSTOMER".equals(normalized)) {
            normalized = AuthAccount.Role.USER.name();
        }

        final AuthAccount.Role role;
        try {
            role = AuthAccount.Role.valueOf(normalized);
        } catch (IllegalArgumentException invalidRole) {
            throw new RegistrationPolicyException(
                    RegistrationRuleViolation.INVALID_ROLE, invalidRole);
        }

        if (!isPublicRole(role)) {
            throw new RegistrationPolicyException(RegistrationRuleViolation.ROLE_NOT_PUBLIC);
        }
        return role;
    }

    public static boolean isPublicRole(AuthAccount.Role role) {
        return role == AuthAccount.Role.USER || role == AuthAccount.Role.SHOP_OWNER;
    }
}
