package com.delivery.auth.application.api;
import java.util.UUID;
public interface SimulationBindingUseCase {
    Binding bind(Long principalId, UUID runId, UUID cohortId);
    BoundToken bindAndIssueAccessToken(Long principalId, UUID runId, UUID cohortId);
    void unbind(Long principalId, UUID runId, long bindingVersion);
    record Binding(UUID runId, UUID cohortId, long bindingVersion) {}
    record BoundToken(Binding binding, String accessToken) {}
}
