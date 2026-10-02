package com.delivery.auth_service.service;

import com.delivery.auth.application.api.SessionTokenPort;
import com.delivery.auth.domain.model.AuthAccount;
import com.delivery.identity.contracts.SimulationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SessionTokenAdapter implements SessionTokenPort {
    private final TokenService tokens;
    @Override public String issueAccessToken(AuthAccount account) {
        SimulationContext context = account.hasActiveSimulationBinding()
                ? new SimulationContext(SimulationContext.ExecutionMode.SIMULATION, account.activeSimulationRunId(),
                        account.simulationCohortId(), account.simulationBindingVersion()) : SimulationContext.real();
        return tokens.generateToken(account.userId(), account.id(), account.email(), account.role().name(), context);
    }
    @Override public String issueRefreshToken(AuthAccount account, String family) {
        return tokens.generateRefreshToken(account.userId(), account.id(), account.email(), account.role().name(), family);
    }
}
