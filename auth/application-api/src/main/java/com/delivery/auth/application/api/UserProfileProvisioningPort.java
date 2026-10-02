package com.delivery.auth.application.api;

import com.delivery.auth.domain.model.AuthAccount;

public interface UserProfileProvisioningPort {
    UserProfileProvisioningReply provision(AuthAccount account);
}
