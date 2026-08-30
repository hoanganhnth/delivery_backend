package com.delivery.auth_service.service;

import com.delivery.auth_service.entity.AuthAccount;
import com.delivery.auth_service.repository.AuthAccountRepository;
import com.delivery.identity.contracts.SimulationContext;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auth is the authority for an actor's simulation eligibility. The lock and
 * monotonically increasing binding version prevent two runs from using one
 * virtual shipper after a stale worker resumes.
 */
@Service
public class SimulationActorBindingService {
    private final AuthAccountRepository accounts;
    private final TokenService tokenService;

    public SimulationActorBindingService(AuthAccountRepository accounts) {
        this(accounts, null);
    }

    @Autowired
    public SimulationActorBindingService(AuthAccountRepository accounts, TokenService tokenService) {
        this.accounts = accounts;
        this.tokenService = tokenService;
    }

    @Transactional
    public SimulationContext bind(Long principalId, UUID runId, UUID cohortId) {
        if (principalId == null || principalId <= 0 || runId == null || cohortId == null) {
            throw new IllegalArgumentException("principalId, runId and cohortId are required");
        }
        AuthAccount account = accounts.findByIdForUpdate(principalId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation actor does not exist"));
        if (!Boolean.TRUE.equals(account.getSimulationActor())) {
            throw new IllegalStateException("Account is not approved as a simulation actor");
        }
        UUID activeRun = account.getActiveSimulationRunId();
        if (activeRun != null && !activeRun.equals(runId)) {
            throw new IllegalStateException("Simulation actor is leased by another simulation run");
        }
        if (account.getSimulationCohortId() != null && !account.getSimulationCohortId().equals(cohortId)) {
            throw new IllegalStateException("Simulation actor belongs to another cohort");
        }
        long nextVersion = Math.max(0L, account.getSimulationBindingVersion() == null
                ? 0L : account.getSimulationBindingVersion()) + 1L;
        account.setSimulationCohortId(cohortId);
        account.setActiveSimulationRunId(runId);
        account.setSimulationBindingVersion(nextVersion);
        return new SimulationContext(SimulationContext.ExecutionMode.SIMULATION, runId, cohortId, nextVersion);
    }

    /**
     * Internal control-plane entry point. Passwords and refresh tokens are
     * never accepted: Auth binds the allowlisted actor then signs a
     * short-lived access token containing that exact fencing context.
     */
    @Transactional
    public BoundSimulationActor bindAndIssueAccessToken(Long principalId, UUID runId, UUID cohortId) {
        if (tokenService == null) {
            throw new IllegalStateException("Simulation token issuer is unavailable");
        }
        SimulationContext context = bind(principalId, runId, cohortId);
        AuthAccount account = accounts.findByIdForUpdate(principalId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation actor does not exist"));
        if (account.getUserId() == null || account.getUserId() <= 0 || account.getEmail() == null
                || account.getEmail().isBlank() || account.getRole() == null) {
            throw new IllegalStateException("Simulation actor is not a provisioned application identity");
        }
        String accessToken = tokenService.generateToken(account.getUserId(), principalId,
                account.getEmail(), account.getRole().name(), context);
        return new BoundSimulationActor(context, accessToken);
    }

    public record BoundSimulationActor(SimulationContext context, String accessToken) {
        public BoundSimulationActor {
            if (context == null || accessToken == null || accessToken.isBlank()) {
                throw new IllegalArgumentException("simulation context and access token are required");
            }
        }
    }

    @Transactional
    public void unbind(Long principalId, UUID runId, long bindingVersion) {
        if (principalId == null || principalId <= 0 || runId == null || bindingVersion <= 0) {
            throw new IllegalArgumentException("principalId, runId and bindingVersion are required");
        }
        AuthAccount account = accounts.findByIdForUpdate(principalId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation actor does not exist"));
        if (!runId.equals(account.getActiveSimulationRunId())
                || !Long.valueOf(bindingVersion).equals(account.getSimulationBindingVersion())) {
            throw new IllegalStateException("Simulation binding fence does not match");
        }
        account.setActiveSimulationRunId(null);
        account.setSimulationBindingVersion(bindingVersion + 1L);
    }
}
