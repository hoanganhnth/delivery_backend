package com.delivery.auth_service.service;
import com.delivery.auth.application.api.SimulationAccessTokenPort;
import com.delivery.auth.application.api.SimulationBindingUseCase;
import com.delivery.auth.domain.model.AuthAccount;
import com.delivery.identity.contracts.SimulationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
public final class SimulationAccessTokenAdapter implements SimulationAccessTokenPort {
    private final TokenService tokens;
    @Override public String issue(AuthAccount account, SimulationBindingUseCase.Binding binding) {
        var context=new SimulationContext(SimulationContext.ExecutionMode.SIMULATION,
                binding.runId(),binding.cohortId(),binding.bindingVersion());
        return tokens.generateToken(account.userId(),account.id(),account.email(),account.role().name(),context);
    }
}
