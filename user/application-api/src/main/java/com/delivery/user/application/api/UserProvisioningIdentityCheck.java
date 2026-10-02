package com.delivery.user.application.api;

/** Core checks executed against rows loaded inside the provisioning transaction. */
public interface UserProvisioningIdentityCheck {
    void existingPrincipal(UserProfileResult existing);
    void existingEmail(UserProfileResult existing);
}
