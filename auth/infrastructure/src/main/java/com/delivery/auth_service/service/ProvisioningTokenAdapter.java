package com.delivery.auth_service.service;

import com.delivery.auth.application.api.ProvisioningTokenPort;
import com.delivery.auth.domain.model.AuthAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProvisioningTokenAdapter implements ProvisioningTokenPort {
    private final TokenService tokens;
    @Override public String issueProvisioningToken(AuthAccount account) {
        return tokens.generateProvisioningToken(account.id(), account.email(), account.role().name());
    }
}
