package com.delivery.simulator.service;

import com.delivery.simulator.config.SimulatorProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class InternalSimulationDeliveryRecoveryClient implements SimulationDeliveryRecoveryClient {
    private final ObjectMapper mapper;
    private final SimulatorProperties properties;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public InternalSimulationDeliveryRecoveryClient(ObjectMapper mapper, SimulatorProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
    }

    @Override
    public List<DeliveryStatus> findByRunId(UUID runId) {
        if (runId == null || properties.getInternalSecret().isBlank()) return List.of();
        try {
            String base = properties.getDeliveryBaseUrl().replaceAll("/+$", "");
            HttpRequest request = HttpRequest.newBuilder(URI.create(base
                    + "/api/deliveries/internal/simulation-runs/" + runId + "/deliveries"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .header("Internal-Token", properties.getInternalSecret())
                    .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Delivery recovery lookup returned HTTP " + response.statusCode());
            }
            JsonNode data = mapper.readTree(response.body()).path("data");
            List<DeliveryStatus> result = new ArrayList<>();
            for (JsonNode item : data) {
                result.add(new DeliveryStatus(item.path("deliveryId").asLong(), item.path("orderId").asLong(),
                        item.path("status").asText("UNKNOWN")));
            }
            return result;
        } catch (Exception error) {
            throw new IllegalStateException("Cannot query Delivery simulation run recovery state", error);
        }
    }
}
