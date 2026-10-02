package com.delivery.auth.application;

import com.delivery.auth.application.api.*;
import com.delivery.auth.domain.model.AuthAccount;
import java.util.UUID;

public final class DefaultSimulationBindingUseCase implements SimulationBindingUseCase {
    private final AuthAccountLockPort accounts;
    private final AuthTransactionPort transactions;
    private final SimulationAccessTokenPort tokens;
    public DefaultSimulationBindingUseCase(AuthAccountLockPort accounts, AuthTransactionPort transactions,
            SimulationAccessTokenPort tokens) {
        this.accounts=accounts;this.transactions=transactions;this.tokens=tokens;
    }
    @Override public Binding bind(Long principalId, UUID runId, UUID cohortId) {
        requireBinding(principalId,runId,cohortId);
        return transactions.required(() -> binding(accounts.updateLocked(principalId,
                account -> claim(account,runId,cohortId)).after()));
    }
    @Override public BoundToken bindAndIssueAccessToken(Long principalId, UUID runId, UUID cohortId) {
        requireBinding(principalId,runId,cohortId);
        return transactions.required(() -> {
            AuthAccount account=accounts.updateLocked(principalId,
                    current -> claim(current,runId,cohortId)).after();
            if (account.userId() == null || account.userId() <= 0 || account.email() == null
                    || account.email().isBlank() || account.role() == null) {
                throw new IllegalStateException("Simulation actor is not a provisioned application identity");
            }
            Binding binding=binding(account);
            String access=tokens.issue(account,binding);
            if (access == null || access.isBlank()) {
                throw new IllegalArgumentException("simulation context and access token are required");
            }
            return new BoundToken(binding,access);
        });
    }
    @Override public void unbind(Long principalId, UUID runId, long version) {
        if (principalId == null || principalId <= 0 || runId == null || version <= 0) {
            throw new IllegalArgumentException("principalId, runId and bindingVersion are required");
        }
        transactions.required(() -> { accounts.updateLocked(principalId, account -> release(account,runId,version)); return null; });
    }
    private static void requireBinding(Long id, UUID run, UUID cohort) {
        if (id == null || id <= 0 || run == null || cohort == null) {
            throw new IllegalArgumentException("principalId, runId and cohortId are required");
        }
    }
    private static AuthAccount claim(AuthAccount account, UUID run, UUID cohort) {
        if (!Boolean.TRUE.equals(account.simulationActor())) {
            throw new IllegalStateException("Account is not approved as a simulation actor");
        }
        if (account.activeSimulationRunId() != null && !account.activeSimulationRunId().equals(run)) {
            throw new IllegalStateException("Simulation actor is leased by another simulation run");
        }
        if (account.simulationCohortId() != null && !account.simulationCohortId().equals(cohort)) {
            throw new IllegalStateException("Simulation actor belongs to another cohort");
        }
        long next=Math.max(0L,account.simulationBindingVersion() == null ? 0L : account.simulationBindingVersion())+1L;
        return account.withSimulationBinding(run,cohort,next);
    }
    private static AuthAccount release(AuthAccount account, UUID run, long version) {
        if (!run.equals(account.activeSimulationRunId())
                || !Long.valueOf(version).equals(account.simulationBindingVersion())) {
            throw new IllegalStateException("Simulation binding fence does not match");
        }
        return account.withoutSimulationBinding(version+1L);
    }
    private static Binding binding(AuthAccount account) {
        return new Binding(account.activeSimulationRunId(),account.simulationCohortId(),account.simulationBindingVersion());
    }
}
