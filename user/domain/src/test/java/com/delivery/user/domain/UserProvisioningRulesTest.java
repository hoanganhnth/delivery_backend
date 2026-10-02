package com.delivery.user.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class UserProvisioningRulesTest {
    @Test
    void requiredClaimsAndBindingAreValidatedBeforePersistence() {
        assertThrows(InvalidProvisioningIdentity.class, () -> UserProvisioningRules.validate(null, 7L, "a@b", "USER"));
        assertThrows(InvalidProvisioningIdentity.class, () -> UserProvisioningRules.validate(7L, null, "a@b", "USER"));
        assertThrows(InvalidProvisioningIdentity.class, () -> UserProvisioningRules.validate(7L, 7L, null, "USER"));
        assertThrows(InvalidProvisioningIdentity.class, () -> UserProvisioningRules.validate(7L, 7L, " ", "USER"));
        assertThrows(InvalidProvisioningIdentity.class, () -> UserProvisioningRules.validate(7L, 7L, "a@b", null));
        assertThrows(InvalidProvisioningIdentity.class, () -> UserProvisioningRules.validate(7L, 7L, "a@b", " "));
        assertThrows(InvalidProvisioningIdentity.class, () -> UserProvisioningRules.validate(7L, 8L, "a@b", "USER"));
        assertDoesNotThrow(() -> UserProvisioningRules.validate(7L, 7L, "a@b", "USER"));
    }

    @Test
    void replayPreservesIdentityAndAllowsEmailCaseDifferences() {
        assertDoesNotThrow(() -> UserProvisioningRules.requireSameIdentity(7L, 7L, "a@b", "USER", 7L, 7L, "A@B", "USER"));
        assertThrows(ProvisioningIdentityConflict.class, () -> UserProvisioningRules.requireSameIdentity(7L, 7L, "a@b", "USER", 8L, 7L, "a@b", "USER"));
        assertThrows(ProvisioningIdentityConflict.class, () -> UserProvisioningRules.requireSameIdentity(7L, 7L, "a@b", "USER", 7L, 8L, "a@b", "USER"));
        assertThrows(ProvisioningIdentityConflict.class, () -> UserProvisioningRules.requireSameIdentity(7L, 7L, "a@b", "USER", 7L, 7L, "c@d", "USER"));
        assertThrows(ProvisioningIdentityConflict.class, () -> UserProvisioningRules.requireSameIdentity(7L, 7L, "a@b", "USER", 7L, 7L, "a@b", "ADMIN"));
        assertDoesNotThrow(() -> UserProvisioningRules.requireEmailOwner(7L, 7L));
        assertThrows(ProvisioningIdentityConflict.class, () -> UserProvisioningRules.requireEmailOwner(7L, 8L));
    }
}
