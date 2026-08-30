package com.delivery.auth_service.controller;

import com.delivery.auth_service.service.SimulationActorBindingService;
import com.delivery.auth_service.exception.AccessDeniedException;
import com.delivery.identity.contracts.SimulationContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Private simulator-to-Auth control-plane API; never exposed through public UI. */
@RestController
@RequestMapping("/api/auth/internal/simulation-actors")
public class SimulationActorInternalController {
    private final SimulationActorBindingService bindings;
    private final String internalSecret;

    public SimulationActorInternalController(SimulationActorBindingService bindings,
                                             @Value("${app.internal.secret:}") String internalSecret) {
        this.bindings = bindings;
        this.internalSecret = internalSecret == null ? "" : internalSecret;
    }

    @PostMapping("/{principalId}/bindings")
    public ResponseEntity<BindResponse> bind(
            @RequestHeader(value = "X-Internal-Secret", required = false) String suppliedSecret,
            @PathVariable Long principalId,
            @RequestBody BindRequest request) {
        authorize(suppliedSecret);
        var actor = bindings.bindAndIssueAccessToken(principalId, request.runId(), request.cohortId());
        return ResponseEntity.ok(new BindResponse(actor.context(), actor.accessToken()));
    }

    @DeleteMapping("/{principalId}/bindings/{runId}")
    public ResponseEntity<Void> unbind(
            @RequestHeader(value = "X-Internal-Secret", required = false) String suppliedSecret,
            @PathVariable Long principalId,
            @PathVariable UUID runId,
            @RequestHeader("X-Simulation-Binding-Version") long bindingVersion) {
        authorize(suppliedSecret);
        bindings.unbind(principalId, runId, bindingVersion);
        return ResponseEntity.noContent().build();
    }

    private void authorize(String suppliedSecret) {
        if (internalSecret.isBlank() || suppliedSecret == null
                || !MessageDigest.isEqual(internalSecret.getBytes(StandardCharsets.UTF_8),
                suppliedSecret.getBytes(StandardCharsets.UTF_8))) {
            throw new AccessDeniedException("simulation actor control-plane unauthorized");
        }
    }

    public record BindRequest(UUID runId, UUID cohortId) {
        public BindRequest {
            if (runId == null || cohortId == null) {
                throw new IllegalArgumentException("runId and cohortId are required");
            }
        }
    }

    public record BindResponse(SimulationContext context, String accessToken) {
    }
}
