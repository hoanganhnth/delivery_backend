package com.delivery.auth.application.api;

import com.delivery.auth.domain.model.AuthAccount;

public interface OperatorProvisioningUseCase {
    AuthAccount provisionAdmin(String email, String password);
    AuthAccount provisionShipper(String email, String password);
}
