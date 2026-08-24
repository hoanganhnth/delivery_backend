package com.delivery.routing_service.service;

import com.delivery.routing_service.api.Coordinate;
import com.delivery.routing_service.api.EtaWindowRequest;
import com.delivery.routing_service.api.EtaWindowResponse;
import com.delivery.routing_service.api.MatrixRequest;
import com.delivery.routing_service.api.MatrixResponse;
import com.delivery.routing_service.api.RouteRequest;
import com.delivery.routing_service.api.RouteResponse;
import com.delivery.routing_service.config.RoutingProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoutingService {

    private final RoutingProperties properties;
    private final ObjectMapper objectMapper;

    private WebClient client() {
        return WebClient.builder().baseUrl(properties.getMapboxBaseUrl()).build();
    }

    public MatrixResponse matrix(MatrixRequest request) {
        validateMatrix(request);
        if (!hasToken()) {
            return fallbackMatrix(request);
        }

        try {
            String coordinates = request.origin().lng() + "," + request.origin().lat() + ";"
                    + request.destinations().stream()
                    .map(destination -> destination.coordinate().lng() + "," + destination.coordinate().lat())
                    .reduce((left, right) -> left + ";" + right)
                    .orElseThrow();
            String profile = request.profile() == null || request.profile().isBlank()
                    ? "driving"
                    : request.profile();
            JsonNode body = client().get()
                    .uri(uri -> uri.path("/directions-matrix/v1/mapbox/")
                            .path(profile)
                            .path("/")
                            .path(coordinates)
                            .queryParam("access_token", properties.getMapboxToken())
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofMillis(properties.getProviderTimeoutMs()))
                    .onErrorResume(error -> Mono.empty())
                    .block();
            if (body == null || !body.has("durations") || !body.has("distances")) {
                return fallbackMatrix(request);
            }
            return parseMatrix(request, body);
        } catch (RuntimeException failure) {
            return fallbackMatrix(request);
        }
    }

    public RouteResponse route(RouteRequest request) {
        validateRoute(request);
        if (!hasToken()) {
            return fallbackRoute(request);
        }

        try {
            String coordinates = request.origin().lng() + "," + request.origin().lat() + ";"
                    + request.destination().lng() + "," + request.destination().lat();
            String profile = request.profile() == null || request.profile().isBlank()
                    ? "driving-traffic"
                    : request.profile();
            JsonNode body = client().get()
                    .uri(uri -> uri.path("/directions/v5/mapbox/")
                            .path(profile)
                            .path("/")
                            .path(coordinates)
                            .queryParam("overview", request.includeGeometry() ? "full" : "false")
                            .queryParam("geometries", "geojson")
                            .queryParam("access_token", properties.getMapboxToken())
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofMillis(properties.getProviderTimeoutMs()))
                    .onErrorResume(error -> Mono.empty())
                    .block();
            if (body == null || !body.has("routes") || !body.path("routes").isArray()
                    || body.path("routes").isEmpty()) {
                return fallbackRoute(request);
            }
            JsonNode route = body.path("routes").get(0);
            String geometry = route.has("geometry") ? route.get("geometry").toString() : null;
            return new RouteResponse(
                    Math.max(0, Math.round(route.path("duration").asDouble())),
                    Math.max(0, Math.round(route.path("distance").asDouble())),
                    geometry,
                    "MAPBOX_DIRECTIONS");
        } catch (RuntimeException failure) {
            return fallbackRoute(request);
        }
    }

    /**
     * ETA authority: driving duration plus restaurant preparation, with the
     * pre-approved ten-minute uncertainty allowance represented as a range.
     */
    public EtaWindowResponse etaWindow(EtaWindowRequest request) {
        if (request == null || request.origin() == null || request.destination() == null
                || request.prepMinutes() == null || request.prepMinutes() < 1
                || request.prepMinutes() > 240) {
            throw new IllegalArgumentException("ETA requires coordinates and prepMinutes between 1 and 240");
        }
        RouteResponse route = route(new RouteRequest(
                "driving", request.origin(), request.destination(), null, false));
        int drivingMinutes = (int) Math.max(1, Math.ceil(route.durationSeconds() / 60d));
        int minimum = Math.addExact(drivingMinutes, request.prepMinutes());
        return new EtaWindowResponse(minimum, Math.addExact(minimum, 10), route.source());
    }

    private MatrixResponse parseMatrix(MatrixRequest request, JsonNode body) {
        List<MatrixResponse.Result> results = new ArrayList<>();
        JsonNode durations = body.path("durations").get(0);
        JsonNode distances = body.path("distances").get(0);
        for (int i = 0; i < request.destinations().size(); i++) {
            double duration = durations != null && durations.has(i) && !durations.get(i).isNull()
                    ? durations.get(i).asDouble() : -1;
            double distance = distances != null && distances.has(i) && !distances.get(i).isNull()
                    ? distances.get(i).asDouble() : -1;
            if (duration < 0 || distance < 0) {
                MatrixResponse.Result fallback = fallbackResult(request.origin(), request.destinations().get(i));
                results.add(fallback);
            } else {
                results.add(new MatrixResponse.Result(request.destinations().get(i).id(),
                        Math.round(duration), Math.round(distance), "MAPBOX_MATRIX"));
            }
        }
        return new MatrixResponse(results, Instant.now());
    }

    private MatrixResponse fallbackMatrix(MatrixRequest request) {
        List<MatrixResponse.Result> results = request.destinations().stream()
                .map(destination -> fallbackResult(request.origin(), destination))
                .toList();
        return new MatrixResponse(results, Instant.now());
    }

    private MatrixResponse.Result fallbackResult(Coordinate origin, MatrixRequest.Destination destination) {
        long meters = Math.round(haversineMeters(origin, destination.coordinate()));
        double metersPerSecond = Math.max(1, properties.getFallbackSpeedKmh()) * 1000d / 3600d;
        return new MatrixResponse.Result(destination.id(), Math.max(1, Math.round(meters / metersPerSecond)), meters,
                "GEODESIC_FALLBACK");
    }

    private RouteResponse fallbackRoute(RouteRequest request) {
        MatrixResponse.Result result = fallbackResult(request.origin(),
                new MatrixRequest.Destination("route", request.destination()));
        return new RouteResponse(result.durationSeconds(), result.distanceMeters(), null, "GEODESIC_FALLBACK");
    }

    private boolean hasToken() {
        return properties.getMapboxToken() != null && !properties.getMapboxToken().isBlank();
    }

    private void validateMatrix(MatrixRequest request) {
        if (request == null || request.origin() == null || request.destinations() == null
                || request.destinations().isEmpty() || request.destinations().size() > 25) {
            throw new IllegalArgumentException("Matrix requires one origin and 1-25 destinations");
        }
    }

    private void validateRoute(RouteRequest request) {
        if (request == null || request.origin() == null || request.destination() == null) {
            throw new IllegalArgumentException("Route origin and destination are required");
        }
    }

    private double haversineMeters(Coordinate a, Coordinate b) {
        double earthRadius = 6_371_000d;
        double lat1 = Math.toRadians(a.lat());
        double lat2 = Math.toRadians(b.lat());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(b.lng() - a.lng());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return earthRadius * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }
}
