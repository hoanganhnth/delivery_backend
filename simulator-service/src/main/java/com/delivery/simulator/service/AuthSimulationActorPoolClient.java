package com.delivery.simulator.service;

import com.delivery.identity.contracts.SimulationContext;
import com.delivery.simulator.config.SimulatorProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Calls Auth only on the private service network with the shared internal secret. */
@Component
public class AuthSimulationActorPoolClient implements SimulationActorPoolClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper;
    private final SimulatorProperties properties;

    public AuthSimulationActorPoolClient(ObjectMapper mapper, SimulatorProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
    }

    @Override
    public BoundActor bind(Long principalId, UUID runId, UUID cohortId) {
        if (principalId == null || principalId <= 0 || runId == null || cohortId == null) {
            throw new IllegalArgumentException("actor principalId, runId and cohortId are required");
        }
        try {
            JsonNode response = exchange("POST", "/api/auth/internal/simulation-actors/" + principalId + "/bindings",
                    mapper.createObjectNode().put("runId", runId.toString()).put("cohortId", cohortId.toString()), null);
            JsonNode contextNode = response.path("context");
            SimulationContext context = mapper.treeToValue(contextNode, SimulationContext.class);
            return new BoundActor(principalId, context, response.path("accessToken").asText());
        } catch (Exception error) {
            throw new IllegalStateException("Cannot bind simulation actor through Auth", error);
        }
    }

    @Override
    public void unbind(Long principalId, UUID runId, long bindingVersion) {
        if (principalId == null || principalId <= 0 || runId == null || bindingVersion <= 0) return;
        try {
            exchange("DELETE", "/api/auth/internal/simulation-actors/" + principalId + "/bindings/" + runId,
                    null, bindingVersion);
        } catch (Exception error) {
            throw new IllegalStateException("Cannot unbind simulation actor through Auth", error);
        }
    }

    private JsonNode exchange(String method, String path, JsonNode body, Long bindingVersion) throws Exception {
        String base = properties.getAuthBaseUrl().replaceAll("/+$", "");
        String secret = properties.getInternalSecret();
        if (base.isBlank() || secret.isBlank()) {
            throw new IllegalStateException("Auth simulation actor control-plane is not configured");
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("X-Internal-Secret", secret);
        if (bindingVersion != null) request.header("X-Simulation-Binding-Version", Long.toString(bindingVersion));
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        }
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Auth returned HTTP " + response.statusCode());
        }
        return response.body() == null || response.body().isBlank()
                ? mapper.createObjectNode() : mapper.readTree(response.body());
    }
}
