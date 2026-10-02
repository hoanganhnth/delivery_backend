package com.delivery.auth.application.api;
import com.delivery.auth.domain.model.AuthAccount;
public interface SimulationAccessTokenPort {
    String issue(AuthAccount account, SimulationBindingUseCase.Binding binding);
}
