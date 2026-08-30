package com.delivery.simulator.service;

import com.delivery.simulator.config.SimulatorProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Small, deliberately boring HTTP adapter for the real Gateway contract.
 * It never logs access tokens or response bodies on failures.
 */
@Component
public class GatewayClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final SimulatorProperties properties;
    private final GatewayFaultInjection faultInjection;

    public GatewayClient(ObjectMapper objectMapper, SimulatorProperties properties,
                         GatewayFaultInjection faultInjection) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.faultInjection = faultInjection;
    }

    public JsonNode get(String path, String bearerToken, String correlationId) {
        return exchange("GET", path, bearerToken, null, correlationId);
    }

    public JsonNode post(String path, String bearerToken, JsonNode body, String correlationId) {
        return exchange("POST", path, bearerToken, body, correlationId);
    }

    public JsonNode postWithHeaders(String path, String bearerToken, JsonNode body,
                                    String correlationId, Map<String, String> headers) {
        return exchange("POST", path, bearerToken, body, correlationId,
                headers == null ? Map.of() : headers);
    }

    public JsonNode put(String path, String bearerToken, JsonNode body, String correlationId) {
        return exchange("PUT", path, bearerToken, body, correlationId);
    }

    private JsonNode exchange(String method, String path, String bearerToken,
                              JsonNode body, String correlationId) {
        return exchange(method, path, bearerToken, body, correlationId, Map.of());
    }

    private JsonNode exchange(String method, String path, String bearerToken,
                              JsonNode body, String correlationId,
                              Map<String, String> additionalHeaders) {
        try {
            if (faultInjection.consumeTransientPollFailure(correlationId, method, path)) {
                throw new GatewayException(429, method + " " + path,
                        "Simulator injected transient poll fault");
            }
            String base = properties.getGatewayBaseUrl().replaceAll("/+$", "");
            String normalizedPath = path.startsWith("/") ? path : "/" + path;
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(base + normalizedPath))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .header("X-Correlation-Id", correlationId == null ? UUID.randomUUID().toString() : correlationId);

            if (bearerToken != null && !bearerToken.isBlank()) {
                builder.header("Authorization", "Bearer " + bearerToken.trim());
            }
            additionalHeaders.forEach((name, value) -> {
                if (name != null && !name.isBlank() && value != null && !value.isBlank()) {
                    builder.header(name, value);
                }
            });

            if (body == null || body.isNull()) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json");
                builder.method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body() == null ? "" : response.body();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new GatewayException(response.statusCode(), method + " " + normalizedPath,
                        "Gateway returned HTTP " + response.statusCode());
            }
            if (responseBody.isBlank()) {
                return NullNode.getInstance();
            }
            return objectMapper.readTree(responseBody);
        } catch (GatewayException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new GatewayException(502, method + " " + path, "Gateway I/O failure");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GatewayException(502, method + " " + path, "Gateway call interrupted");
        } catch (RuntimeException exception) {
            throw new GatewayException(502, method + " " + path, "Invalid Gateway response or target");
        }
    }

    public static final class GatewayException extends RuntimeException {
        private final int status;
        private final String operation;

        public GatewayException(int status, String operation, String message) {
            super(message);
            this.status = status;
            this.operation = operation;
        }

        public int getStatus() {
            return status;
        }

        public String getOperation() {
            return operation;
        }
    }
}
