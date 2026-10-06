package com.delivery.routing.infrastructure.provider;

import com.delivery.routing.domain.*;
import com.delivery.routing.infrastructure.config.RoutingProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class MapboxRoutingProviderHttpTest {
    private final Coordinate origin = new Coordinate(10.76, 106.66);
    private final Coordinate destination = new Coordinate(10.78, 106.68);
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> request = new AtomicReference<>();
    private HttpServer server;
    private MapboxRoutingProviderAdapter adapter;
    private int status = 200;

    @BeforeEach
    void startProvider() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            request.set(exchange.getRequestURI().toString());
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        RoutingProperties properties = new RoutingProperties();
        properties.setMapboxToken("test-token");
        properties.setMapboxBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setProviderTimeoutMs(3000);
        adapter = new MapboxRoutingProviderAdapter(properties);
    }

    @AfterEach
    void stopProvider() { server.stop(0); }

    @Test
    void directionsPreserveGeometryRoundMetricsAndSendCoordinatesInProviderOrder() {
        body.set("{\"routes\":[{\"duration\":12.6,\"distance\":99.4,\"geometry\":{\"type\":\"LineString\",\"coordinates\":[]}}]}");
        RouteResult result = adapter.route(new RouteQuery(null, origin, destination, null, true));
        assertThat(result).isEqualTo(new RouteResult(13, 99,
                "{\"type\":\"LineString\",\"coordinates\":[]}", "MAPBOX_DIRECTIONS"));
        assertThat(request.get()).contains("/directions/v5/mapbox/driving-traffic/106.66,10.76;106.68,10.78",
                "overview=full", "geometries=geojson", "access_token=test-token");
    }

    @Test
    void directionsClampNegativeMetricsAndAllowMissingGeometry() {
        body.set("{\"routes\":[{\"duration\":-20,\"distance\":-50}]}");
        assertThat(adapter.route(new RouteQuery("cycling", origin, destination, null, false)))
                .isEqualTo(new RouteResult(0, 0, null, "MAPBOX_DIRECTIONS"));
        assertThat(request.get()).contains("/cycling/", "overview=false");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"routes\":[]}", "{\"routes\":{}}", "invalid-json"})
    void invalidDirectionsResponseRequestsApplicationFallback(String response) {
        body.set(response);
        assertThat(adapter.route(new RouteQuery("", origin, destination, null, false))).isNull();
    }

    @Test
    void matrixPreservesDestinationIdentityAndRoundsAndClampsMetrics() {
        body.set("{\"durations\":[[10.6,-2]],\"distances\":[[90.4,-1]]}");
        List<MatrixResult> results = adapter.matrix(matrix());
        assertThat(results).containsExactly(new MatrixResult("first", 11, 90, "MAPBOX_MATRIX"),
                new MatrixResult("second", 0, 0, "MAPBOX_MATRIX"));
        assertThat(request.get()).contains("/directions-matrix/v1/mapbox/driving/106.66,10.76;106.68,10.78;106.68,10.78");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"durations\":[]}", "{\"durations\":[],\"distances\":[]}",
            "{\"durations\":[[1]],\"distances\":[[2]]}",
            "{\"durations\":[[null,1]],\"distances\":[[2,3]]}",
            "{\"durations\":[[1,2]],\"distances\":[[2,null]]}", "invalid-json"})
    void incompleteOrUnreachableMatrixRequestsFallbackForWholeQuery(String response) {
        body.set(response);
        assertThat(adapter.matrix(matrix())).isNull();
    }

    @Test
    void providerHttpErrorRequestsFallbackForBothOperations() {
        status = 503;
        body.set("{}");
        assertThat(adapter.route(new RouteQuery("driving", origin, destination, null, false))).isNull();
        assertThat(adapter.matrix(matrix())).isNull();
    }

    private MatrixQuery matrix() {
        return new MatrixQuery("", origin, List.of(new MatrixQuery.Destination("first", destination),
                new MatrixQuery.Destination("second", destination)), null);
    }
}
