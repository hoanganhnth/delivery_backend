package com.delivery.routing.client;

import com.delivery.routing.contracts.EtaWindowRequest;
import com.delivery.routing.contracts.EtaWindowResponse;
import com.delivery.routing.contracts.MatrixRequest;
import com.delivery.routing.contracts.MatrixResponse;
import com.delivery.routing.contracts.RouteRequest;
import com.delivery.routing.contracts.RouteResponse;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/** Blocking HTTP implementation of the typed routing client. */
public final class HttpRoutingClient implements RoutingClient {

    public static final String INTERNAL_TOKEN_HEADER = "Internal-Token";

    private final RoutingHttpExchange exchange;
    private final URI routingBaseUri;
    private final String internalToken;

    public HttpRoutingClient(
            RoutingHttpExchange exchange, URI routingBaseUri, String internalToken) {
        this.exchange = Objects.requireNonNull(exchange, "exchange");
        this.routingBaseUri = validateBaseUri(routingBaseUri);
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalArgumentException("internalToken must not be blank");
        }
        this.internalToken = internalToken;
    }

    public HttpRoutingClient(RestClient restClient, URI routingBaseUri, String internalToken) {
        this(new RestClientRoutingHttpExchange(restClient), routingBaseUri, internalToken);
    }

    @Override
    public RouteResponse getRoute(RouteRequest request) {
        return post("route", request, RouteResponse.class);
    }

    @Override
    public MatrixResponse getMatrix(MatrixRequest request) {
        return post("matrix", request, MatrixResponse.class);
    }

    @Override
    public EtaWindowResponse getEtaWindow(EtaWindowRequest request) {
        return post("eta-window", request, EtaWindowResponse.class);
    }

    private <T> T post(String endpoint, Object request, Class<T> responseType) {
        Objects.requireNonNull(request, "request");
        URI uri = UriComponentsBuilder.fromUri(routingBaseUri)
                .pathSegment("internal", "routing", "v1", endpoint)
                .build()
                .toUri();
        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_TOKEN_HEADER, internalToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        try {
            ResponseEntity<T> response = exchange.post(uri, headers, request, responseType);
            if (response == null || response.getStatusCode() == null) {
                throw remoteFailure("Routing service returned no HTTP response", null);
            }
            HttpStatusCode status = response.getStatusCode();
            if (!status.is2xxSuccessful()) {
                throw classify(status, null);
            }
            if (response.getBody() == null) {
                throw remoteFailure("Routing service returned an empty response", status);
            }
            return response.getBody();
        } catch (RestClientResponseException responseFailure) {
            throw classify(responseFailure.getStatusCode(), responseFailure);
        } catch (ResourceAccessException unavailable) {
            throw new RoutingClientException(
                    RoutingClientFailure.UNAVAILABLE,
                    "Routing service is unavailable",
                    null,
                    unavailable);
        } catch (RestClientException protocolFailure) {
            throw new RoutingClientException(
                    RoutingClientFailure.REMOTE_FAILURE,
                    "Routing response could not be decoded",
                    null,
                    protocolFailure);
        }
    }

    private RoutingClientException classify(HttpStatusCode status, Throwable failure) {
        RoutingClientFailure kind;
        if (status == null) {
            kind = RoutingClientFailure.REMOTE_FAILURE;
        } else if (status.value() == HttpStatus.BAD_REQUEST.value()) {
            kind = RoutingClientFailure.INVALID_REQUEST;
        } else if (status.value() == HttpStatus.UNAUTHORIZED.value()) {
            kind = RoutingClientFailure.UNAUTHORIZED;
        } else if (status.value() == HttpStatus.FORBIDDEN.value()) {
            kind = RoutingClientFailure.FORBIDDEN;
        } else if (status.value() == HttpStatus.NOT_FOUND.value()) {
            kind = RoutingClientFailure.NOT_FOUND;
        } else if (status.is5xxServerError()) {
            kind = RoutingClientFailure.UNAVAILABLE;
        } else {
            kind = RoutingClientFailure.REMOTE_FAILURE;
        }
        return new RoutingClientException(kind, "Routing request failed", status, failure);
    }

    private RoutingClientException remoteFailure(
            String message, HttpStatusCode status) {
        return new RoutingClientException(
                RoutingClientFailure.REMOTE_FAILURE, message, status, null);
    }

    private static URI validateBaseUri(URI uri) {
        Objects.requireNonNull(uri, "routingBaseUri");
        if (!uri.isAbsolute()
                || uri.getHost() == null
                || !("http".equalsIgnoreCase(uri.getScheme())
                        || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("routingBaseUri must be an absolute HTTP(S) URI");
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("routingBaseUri must not contain query or fragment");
        }
        return uri;
    }
}
