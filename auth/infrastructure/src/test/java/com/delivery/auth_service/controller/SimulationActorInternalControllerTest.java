package com.delivery.auth_service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.delivery.auth.application.api.SimulationBindingUseCase;
import com.delivery.identity.contracts.SimulationContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SimulationActorInternalControllerTest {

    @Test
    void internalSecretCanBindAnActorAndReceivesOnlyAnAccessToken() {
        SimulationBindingUseCase bindings = mock(SimulationBindingUseCase.class);
        UUID runId = UUID.randomUUID();
        UUID cohortId = UUID.randomUUID();
        SimulationContext context = new SimulationContext(SimulationContext.ExecutionMode.SIMULATION,
                runId, cohortId, 1L);
        when(bindings.bindAndIssueAccessToken(9L, runId, cohortId))
                .thenReturn(new SimulationBindingUseCase.BoundToken(
                        new SimulationBindingUseCase.Binding(runId,cohortId,1L), "jwt"));
        SimulationActorInternalController controller = new SimulationActorInternalController(bindings, "internal-test-secret");

        var response = controller.bind("internal-test-secret", 9L,
                new SimulationActorInternalController.BindRequest(runId, cohortId));

        assertThat(response.getBody().accessToken()).isEqualTo("jwt");
        assertThat(response.getBody().context()).isEqualTo(context);
    }

    @Test
    void blankOrWrongInternalSecretFailsClosed() {
        SimulationBindingUseCase bindings = mock(SimulationBindingUseCase.class);
        SimulationActorInternalController controller = new SimulationActorInternalController(bindings, "");

        assertThatThrownBy(() -> controller.bind("", 9L,
                new SimulationActorInternalController.BindRequest(UUID.randomUUID(), UUID.randomUUID())))
                .isInstanceOf(com.delivery.auth_service.exception.AccessDeniedException.class)
                .hasMessageContaining("unauthorized");
    }
}
