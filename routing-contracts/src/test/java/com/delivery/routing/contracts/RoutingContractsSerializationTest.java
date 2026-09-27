package com.delivery.routing.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoutingContractsSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final Coordinate origin = new Coordinate(10.76, 106.66);
    private final Coordinate destination = new Coordinate(10.78, 106.68);
    private final Instant departureAt = Instant.parse("2026-09-27T12:30:00Z");

    @Test
    void preservesCoordinateJsonShapeAndRoundTrips() throws Exception {
        assertRoundTrip(new Coordinate(10.76, 106.66),
                "{\"lat\":10.76,\"lng\":106.66}", Coordinate.class);
    }

    @Test
    void preservesRouteJsonShapeAndRoundTrips() throws Exception {
        assertRoundTrip(
                new RouteRequest("driving", origin, destination, departureAt, true),
                "{\"profile\":\"driving\",\"origin\":{\"lat\":10.76,\"lng\":106.66},"
                        + "\"destination\":{\"lat\":10.78,\"lng\":106.68},"
                        + "\"departureAt\":\"2026-09-27T12:30:00Z\",\"includeGeometry\":true}",
                RouteRequest.class);
        assertRoundTrip(
                new RouteResponse(900, 4200, "{\"type\":\"LineString\"}", "MAPBOX_DIRECTIONS"),
                "{\"durationSeconds\":900,\"distanceMeters\":4200,"
                        + "\"geometry\":\"{\\\"type\\\":\\\"LineString\\\"}\","
                        + "\"source\":\"MAPBOX_DIRECTIONS\"}",
                RouteResponse.class);
    }

    @Test
    void preservesMatrixJsonShapeAndRoundTrips() throws Exception {
        assertRoundTrip(
                new MatrixRequest("driving", origin,
                        List.of(new MatrixRequest.Destination("destination", destination)), departureAt),
                "{\"profile\":\"driving\",\"origin\":{\"lat\":10.76,\"lng\":106.66},"
                        + "\"destinations\":[{\"id\":\"destination\","
                        + "\"coordinate\":{\"lat\":10.78,\"lng\":106.68}}],"
                        + "\"departureAt\":\"2026-09-27T12:30:00Z\"}",
                MatrixRequest.class);
        assertRoundTrip(
                new MatrixResponse(
                        List.of(new MatrixResponse.Result("destination", 900, 4200, "MAPBOX_MATRIX")),
                        departureAt),
                "{\"results\":[{\"id\":\"destination\",\"durationSeconds\":900,"
                        + "\"distanceMeters\":4200,\"source\":\"MAPBOX_MATRIX\"}],"
                        + "\"generatedAt\":\"2026-09-27T12:30:00Z\"}",
                MatrixResponse.class);
    }

    @Test
    void preservesEtaWindowJsonShapeAndRoundTrips() throws Exception {
        assertRoundTrip(
                new EtaWindowRequest(origin, destination, 15),
                "{\"origin\":{\"lat\":10.76,\"lng\":106.66},"
                        + "\"destination\":{\"lat\":10.78,\"lng\":106.68},\"prepMinutes\":15}",
                EtaWindowRequest.class);
        assertRoundTrip(
                new EtaWindowResponse(20, 30, "GEODESIC_FALLBACK"),
                "{\"minMinutes\":20,\"maxMinutes\":30,\"source\":\"GEODESIC_FALLBACK\"}",
                EtaWindowResponse.class);
    }

    private <T> void assertRoundTrip(T source, String expectedJson, Class<T> type) throws Exception {
        String json = mapper.writeValueAsString(source);

        assertThat(json).isEqualTo(expectedJson);
        assertThat(mapper.readValue(json, type)).isEqualTo(source);
    }
}
