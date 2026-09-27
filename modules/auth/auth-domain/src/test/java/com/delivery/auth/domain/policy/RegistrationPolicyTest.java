package com.delivery.auth.domain.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.delivery.auth.domain.model.AuthAccount;
import org.junit.jupiter.api.Test;

class RegistrationPolicyTest {

    @Test
    void publicRegistrationAllowsUserShopOwnerAndCustomerAlias() {
        assertEquals(AuthAccount.Role.USER, RegistrationPolicy.parsePublicRole(" customer "));
        assertEquals(AuthAccount.Role.USER, RegistrationPolicy.parsePublicRole("USER"));
        assertEquals(AuthAccount.Role.SHOP_OWNER, RegistrationPolicy.parsePublicRole("shop_owner"));
        assertTrue(RegistrationPolicy.isPublicRole(AuthAccount.Role.USER));
    }

    @Test
    void privilegedRolesCannotUsePublicRegistration() {
        RegistrationPolicyException admin = assertThrows(
                RegistrationPolicyException.class,
                () -> RegistrationPolicy.parsePublicRole("ADMIN"));
        RegistrationPolicyException shipper = assertThrows(
                RegistrationPolicyException.class,
                () -> RegistrationPolicy.parsePublicRole("SHIPPER"));

        assertEquals(RegistrationRuleViolation.ROLE_NOT_PUBLIC, admin.violation());
        assertEquals(RegistrationRuleViolation.ROLE_NOT_PUBLIC, shipper.violation());
        assertEquals(false, RegistrationPolicy.isPublicRole(AuthAccount.Role.ADMIN));
        assertEquals(false, RegistrationPolicy.isPublicRole(AuthAccount.Role.SHIPPER));
    }

    @Test
    void missingOrUnknownRoleProducesStructuredFailure() {
        RegistrationPolicyException missing = assertThrows(
                RegistrationPolicyException.class,
                () -> RegistrationPolicy.parsePublicRole("  "));
        RegistrationPolicyException nullRole = assertThrows(
                RegistrationPolicyException.class,
                () -> RegistrationPolicy.parsePublicRole(null));
        RegistrationPolicyException unknown = assertThrows(
                RegistrationPolicyException.class,
                () -> RegistrationPolicy.parsePublicRole("moderator"));

        assertEquals(RegistrationRuleViolation.ROLE_REQUIRED, missing.violation());
        assertEquals(RegistrationRuleViolation.ROLE_REQUIRED, nullRole.violation());
        assertEquals(RegistrationRuleViolation.INVALID_ROLE, unknown.violation());
    }
}
