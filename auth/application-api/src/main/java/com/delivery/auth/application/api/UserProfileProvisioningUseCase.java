package com.delivery.auth.application.api;

import com.delivery.auth.domain.model.AuthAccount;

public interface UserProfileProvisioningUseCase {
    AuthAccount provision(AuthAccount account);
}
