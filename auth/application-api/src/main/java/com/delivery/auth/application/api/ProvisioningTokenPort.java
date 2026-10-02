package com.delivery.auth.application.api;

import com.delivery.auth.domain.model.AuthAccount;

public interface ProvisioningTokenPort {
    String issueProvisioningToken(AuthAccount account);
}
