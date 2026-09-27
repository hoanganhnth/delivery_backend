package com.delivery.routing.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.delivery.routing.contracts.Coordinate;
import com.delivery.routing.contracts.EtaWindowRequest;
import com.delivery.routing.contracts.EtaWindowResponse;
import com.delivery.routing.contracts.MatrixRequest;
import com.delivery.routing.contracts.MatrixResponse;
import com.delivery.routing.contracts.RouteRequest;
import com.delivery.routing.contracts.RouteResponse;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

class HttpRoutingClientTest {

    private static final URI BASE_URI = URI.create("http://routing.test/base");
    private static final String TOKEN = "order-routing-secret";
    private static final Coordinate ORIGIN = new Coordinate(10.77, 106.69);
    private static final Coordinate DESTINATION = new Coordinate(10.78, 106.70);

    @Test
    void sendsRouteRequestToTypedEndpointWithTargetCredential() {
        RouteRequest request = new RouteRequest(
                "driving-traffic", ORIGIN, DESTINATION, Instant.parse("2026-09-27T10:15:30Z"), false);
        RecordingExchange exchange = new RecordingExchange(
                ResponseEntity.ok(new RouteResponse(900, 4200, null, "MAPBOX_DIRECTIONS")));

        RouteResponse response = client(exchange).getRoute(request);

        assertThat(response.durationSeconds()).isEqualTo(900);
        assertThat(exchange.call().uri()).isEqualTo(
                URI.create("http://routing.test/base/internal/routing/v1/route"));
        assertThat(exchange.call().request()).isSameAs(request);
        assertThat(exchange.call().headers().getFirst(HttpRoutingClient.INTERNAL_TOKEN_HEADER))
                .isEqualTo(TOKEN);
        assertThat(exchange.call().headers().getContentType().toString())
                .isEqualTo("application/json");
    }

    @Test
    void sendsMatrixAndEtaWindowRequestsToTheirTypedEndpoints() {
        MatrixRequest matrixRequest = new MatrixRequest(
                "driving", ORIGIN,
                List.of(new MatrixRequest.Destination("restaurant-7", DESTINATION)), null);
        RecordingExchange matrixExchange = new RecordingExchange(
                ResponseEntity.ok(new MatrixResponse(List.of(), Instant.parse("2026-09-27T10:15:30Z"))));
        MatrixResponse matrix = client(matrixExchange).getMatrix(matrixRequest);
        assertThat(matrix.results()).isEmpty();
        assertThat(matrixExchange.call().uri()).isEqualTo(
                URI.create("http://routing.test/base/internal/routing/v1/matrix"));
        assertThat(matrixExchange.call().request()).isSameAs(matrixRequest);

        EtaWindowRequest etaRequest = new EtaWindowRequest(ORIGIN, DESTINATION, 15);
        RecordingExchange etaExchange = new RecordingExchange(
                ResponseEntity.ok(new EtaWindowResponse(20, 30, "GEODESIC_FALLBACK")));
        EtaWindowResponse eta = client(etaExchange).getEtaWindow(etaRequest);
        assertThat(eta.minMinutes()).isEqualTo(20);
        assertThat(etaExchange.call().uri()).isEqualTo(
                URI.create("http://routing.test/base/internal/routing/v1/eta-window"));
        assertThat(etaExchange.call().request()).isSameAs(etaRequest);
    }

    @Test
    void classifiesClientAndServerStatusResponses() {
        assertResponseFailure(HttpStatus.BAD_REQUEST, RoutingClientFailure.INVALID_REQUEST);
        assertResponseFailure(HttpStatus.UNAUTHORIZED, RoutingClientFailure.UNAUTHORIZED);
        assertResponseFailure(HttpStatus.FORBIDDEN, RoutingClientFailure.FORBIDDEN);
        assertResponseFailure(HttpStatus.NOT_FOUND, RoutingClientFailure.NOT_FOUND);
        assertResponseFailure(HttpStatus.INTERNAL_SERVER_ERROR, RoutingClientFailure.UNAVAILABLE);
    }

    @Test
    void classifiesThrownStatusAndNetworkFailures() {
        RecordingExchange unauthorized = new RecordingExchange(null);
        unauthorized.failure = new RestClientResponseException(
                "unauthorized", 401, "Unauthorized", HttpHeaders.EMPTY, new byte[0], null);
        assertFailure(client(unauthorized), RoutingClientFailure.UNAUTHORIZED);
        assertThat(unauthorized.calls()).isOne();

        RecordingExchange unavailable = new RecordingExchange(null);
        unavailable.failure = new ResourceAccessException("connection refused");
        assertFailure(client(unavailable), RoutingClientFailure.UNAVAILABLE);
        assertThat(unavailable.calls()).isOne();
    }

    @Test
    void classifiesDecodeFailuresAndEmptySuccessfulResponses() {
        RecordingExchange malformed = new RecordingExchange(null);
        malformed.failure = new RestClientException("malformed JSON");
        assertFailure(client(malformed), RoutingClientFailure.REMOTE_FAILURE);

        assertFailure(
                client(new RecordingExchange(ResponseEntity.ok().build())),
                RoutingClientFailure.REMOTE_FAILURE);
    }

    @Test
    void rejectsInvalidConfigurationAndRequestsBeforeNetwork() {
        RecordingExchange exchange = new RecordingExchange(ResponseEntity.ok(
                new RouteResponse(1, 1, null, "TEST")));
        assertThatThrownBy(() -> client(exchange).getRoute(null))
                .isInstanceOf(NullPointerException.class);
        assertThat(exchange.calls()).isZero();

        assertThatThrownBy(() -> new HttpRoutingClient(
                exchange, URI.create("/routing"), TOKEN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HttpRoutingClient(
                exchange, URI.create("http://routing.test"), " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HttpRoutingClient(
                exchange, URI.create("http://routing.test?tenant=secret"), TOKEN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private HttpRoutingClient client(RoutingHttpExchange exchange) {
        return new HttpRoutingClient(exchange, BASE_URI, TOKEN);
    }

    private void assertResponseFailure(
            HttpStatus status, RoutingClientFailure expectedFailure) {
        assertFailure(
                client(new RecordingExchange(ResponseEntity.status(status).build())),
                expectedFailure);
    }

    private void assertFailure(RoutingClient client, RoutingClientFailure expectedFailure) {
        assertThatThrownBy(() -> client.getRoute(new RouteRequest(
                "driving", ORIGIN, DESTINATION, null, false)))
                .isInstanceOf(RoutingClientException.class)
                .satisfies(error -> {
                    RoutingClientException exception = (RoutingClientException) error;
                    assertThat(exception.kind()).isEqualTo(expectedFailure);
                });
    }

    private record Call(URI uri, HttpHeaders headers, Object request, Class<?> responseType) {
    }

    private static final class RecordingExchange implements RoutingHttpExchange {
        private final ResponseEntity<?> response;
        private RuntimeException failure;
        private Call call;
        private int calls;

        private RecordingExchange(ResponseEntity<?> response) {
            this.response = response;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> ResponseEntity<T> post(
                URI uri, HttpHeaders headers, Object request, Class<T> responseType) {
            calls++;
            call = new Call(uri, headers, request, responseType);
            if (failure != null) {
                throw failure;
            }
            return (ResponseEntity<T>) response;
        }

        private Call call() {
            return call;
        }

        private int calls() {
            return calls;
        }
    }
}
