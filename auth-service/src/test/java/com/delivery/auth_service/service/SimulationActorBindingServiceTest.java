package com.delivery.auth_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.delivery.auth_service.entity.AuthAccount;
import com.delivery.auth_service.repository.AuthAccountRepository;
import com.delivery.identity.contracts.SimulationContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SimulationActorBindingServiceTest {

    @Test
    void bindsAnEligibleSimulationActorToExactlyOneRunAndIncrementsFence() {
        AuthAccount account = shipper();
        AuthAccountRepository repository = Mockito.mock(AuthAccountRepository.class);
        when(repository.findByIdForUpdate(9L)).thenReturn(Optional.of(account));
        SimulationActorBindingService service = new SimulationActorBindingService(repository);
        UUID runId = UUID.randomUUID();
        UUID cohortId = UUID.randomUUID();

        var context = service.bind(9L, runId, cohortId);

        assertThat(context.runId()).isEqualTo(runId);
        assertThat(context.cohortId()).isEqualTo(cohortId);
        assertThat(context.bindingVersion()).isEqualTo(1L);
        assertThat(account.getActiveSimulationRunId()).isEqualTo(runId);
    }

    @Test
    void rejectsBindingAnActorLeasedByAnotherRun() {
        AuthAccount account = shipper();
        account.setActiveSimulationRunId(UUID.randomUUID());
        account.setSimulationCohortId(UUID.randomUUID());
        account.setSimulationBindingVersion(1L);
        AuthAccountRepository repository = Mockito.mock(AuthAccountRepository.class);
        when(repository.findByIdForUpdate(9L)).thenReturn(Optional.of(account));
        SimulationActorBindingService service = new SimulationActorBindingService(repository);

        assertThatThrownBy(() -> service.bind(9L, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("another simulation run");
    }

    @Test
    void issuesOnlyAServerSignedSimulationTokenForTheNewBindingFence() {
        AuthAccount account = shipper();
        account.setUserId(101L);
        account.setEmail("virtual-shipper@example.test");
        AuthAccountRepository repository = Mockito.mock(AuthAccountRepository.class);
        TokenService tokens = Mockito.mock(TokenService.class);
        when(repository.findByIdForUpdate(9L)).thenReturn(Optional.of(account));
        when(tokens.generateToken(101L, 9L, "virtual-shipper@example.test", "SHIPPER",
                new SimulationContext(SimulationContext.ExecutionMode.SIMULATION,
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        UUID.fromString("22222222-2222-2222-2222-222222222222"), 1L)))
                .thenReturn("server-signed-access-token");
        SimulationActorBindingService service = new SimulationActorBindingService(repository, tokens);

        var actor = service.bindAndIssueAccessToken(9L,
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"));

        assertThat(actor.accessToken()).isEqualTo("server-signed-access-token");
        assertThat(actor.context().bindingVersion()).isEqualTo(1L);
        verify(tokens).generateToken(101L, 9L, "virtual-shipper@example.test", "SHIPPER",
                actor.context());
    }

    private AuthAccount shipper() {
        AuthAccount account = new AuthAccount();
        account.setRole(AuthAccount.Role.SHIPPER);
        account.setSimulationActor(true);
        return account;
    }
}
