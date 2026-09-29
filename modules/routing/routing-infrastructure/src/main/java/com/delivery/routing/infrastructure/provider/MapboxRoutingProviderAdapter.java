package com.delivery.routing.infrastructure.provider;

import com.delivery.routing.application.api.RoutingProviderPort;
import com.delivery.routing.domain.MatrixQuery;
import com.delivery.routing.domain.MatrixResult;
import com.delivery.routing.domain.RouteQuery;
import com.delivery.routing.domain.RouteResult;
import com.delivery.routing.infrastructure.config.RoutingProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** Mapbox HTTP adapter; provider failure is represented by null for application fallback. */
public final class MapboxRoutingProviderAdapter implements RoutingProviderPort {
    private final RoutingProperties properties;
    private final WebClient client;

    public MapboxRoutingProviderAdapter(RoutingProperties properties) {
        this.properties = properties;
        this.client = WebClient.builder().baseUrl(properties.getMapboxBaseUrl()).build();
    }

    @Override
    public RouteResult route(RouteQuery query) {
        if (!hasToken()) return null;
        try {
            JsonNode body = client.get().uri(uri -> uri.path("/directions/v5/mapbox/")
                    .path(profile(query.profile(), "driving-traffic")).path("/")
                    .path(coordinates(query.origin().lng(), query.origin().lat(), query.destination().lng(), query.destination().lat()))
                    .queryParam("overview", query.includeGeometry() ? "full" : "false")
                    .queryParam("geometries", "geojson").queryParam("access_token", properties.getMapboxToken()).build())
                    .accept(MediaType.APPLICATION_JSON).retrieve().bodyToMono(JsonNode.class)
                    .timeout(Duration.ofMillis(properties.getProviderTimeoutMs())).onErrorResume(error -> Mono.empty()).block();
            if (body == null || !body.path("routes").isArray() || body.path("routes").isEmpty()) return null;
            JsonNode route = body.path("routes").get(0);
            return new RouteResult(Math.max(0, Math.round(route.path("duration").asDouble())),
                    Math.max(0, Math.round(route.path("distance").asDouble())),
                    route.has("geometry") ? route.get("geometry").toString() : null, "MAPBOX_DIRECTIONS");
        } catch (RuntimeException failure) { return null; }
    }

    @Override
    public List<MatrixResult> matrix(MatrixQuery query) {
        if (!hasToken()) return null;
        try {
            String destinations = query.destinations().stream()
                    .map(d -> d.coordinate().lng() + "," + d.coordinate().lat()).reduce((a, b) -> a + ";" + b).orElseThrow();
            JsonNode body = client.get().uri(uri -> uri.path("/directions-matrix/v1/mapbox/")
                    .path(profile(query.profile(), "driving")).path("/")
                    .path(query.origin().lng() + "," + query.origin().lat() + ";" + destinations)
                    .queryParam("access_token", properties.getMapboxToken()).build())
                    .accept(MediaType.APPLICATION_JSON).retrieve().bodyToMono(JsonNode.class)
                    .timeout(Duration.ofMillis(properties.getProviderTimeoutMs())).onErrorResume(error -> Mono.empty()).block();
            if (body == null || !body.has("durations") || !body.has("distances")) return null;
            JsonNode durations = body.path("durations").get(0), distances = body.path("distances").get(0);
            var results = new java.util.ArrayList<MatrixResult>();
            for (int i = 0; i < query.destinations().size(); i++) {
                if (durations == null || distances == null || durations.get(i) == null || distances.get(i) == null
                        || durations.get(i).isNull() || distances.get(i).isNull()) return null;
                results.add(new MatrixResult(query.destinations().get(i).id(), Math.max(0, Math.round(durations.get(i).asDouble())),
                        Math.max(0, Math.round(distances.get(i).asDouble())), "MAPBOX_MATRIX"));
            }
            return results;
        } catch (RuntimeException failure) { return null; }
    }

    private boolean hasToken() { return properties.getMapboxToken() != null && !properties.getMapboxToken().isBlank(); }
    private String profile(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private String coordinates(double olng, double olat, double dlng, double dlat) { return olng + "," + olat + ";" + dlng + "," + dlat; }
}
