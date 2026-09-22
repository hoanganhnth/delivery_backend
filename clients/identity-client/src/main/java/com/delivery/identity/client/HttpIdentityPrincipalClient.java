package com.delivery.identity.client;

import com.delivery.identity.contracts.IdentityPrincipal;
import com.delivery.platform.http.blocking.BlockingHttpExchange;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

public final class HttpIdentityPrincipalClient implements IdentityPrincipalClient {

    public static final String INTERNAL_TOKEN_HEADER = "Internal-Token";

    private final BlockingHttpExchange exchange;
    private final URI authBaseUri;
    private final String internalToken;

    public HttpIdentityPrincipalClient(
            BlockingHttpExchange exchange, URI authBaseUri, String internalToken) {
        this.exchange = Objects.requireNonNull(exchange, "exchange");
        this.authBaseUri = Objects.requireNonNull(authBaseUri, "authBaseUri");
        if (!authBaseUri.isAbsolute()) {
            throw new IllegalArgumentException("authBaseUri must be absolute");
        }
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalArgumentException("internalToken must not be blank");
        }
        this.internalToken = internalToken;
    }

    @Override
    public Optional<IdentityPrincipal> findByPrincipalId(long principalId) {
        if (principalId <= 0) {
            throw new IllegalArgumentException("principalId must be positive");
        }
        URI uri = UriComponentsBuilder.fromUri(authBaseUri)
                .pathSegment("api", "auth", "internal", "principals", Long.toString(principalId))
                .build()
                .toUri();
        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_TOKEN_HEADER, internalToken);
        try {
            ResponseEntity<IdentityPrincipal> response =
                    exchange.get(uri, headers, IdentityPrincipal.class);
            if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw classify(response.getStatusCode(), null);
            }
            IdentityPrincipal principal = response.getBody();
            if (principal == null
                    || !Long.valueOf(principalId).equals(principal.principalId())
                    || principal.role() == null
                    || principal.lifecycleStatus() == null) {
                throw new IdentityClientException(
                        IdentityClientFailure.REMOTE_FAILURE,
                        "Identity service returned an invalid principal",
                        null);
            }
            return Optional.of(principal);
        } catch (RestClientResponseException responseFailure) {
            if (responseFailure.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw classify(responseFailure.getStatusCode(), responseFailure);
        } catch (ResourceAccessException unavailable) {
            throw new IdentityClientException(
                    IdentityClientFailure.UNAVAILABLE, "Identity service is unavailable", unavailable);
        } catch (RestClientException protocolFailure) {
            throw new IdentityClientException(
                    IdentityClientFailure.REMOTE_FAILURE,
                    "Identity response could not be decoded",
                    protocolFailure);
        }
    }

    private IdentityClientException classify(HttpStatusCode status, Throwable failure) {
        IdentityClientFailure kind;
        if (status == HttpStatus.BAD_REQUEST) {
            kind = IdentityClientFailure.INVALID_REQUEST;
        } else if (status == HttpStatus.FORBIDDEN) {
            kind = IdentityClientFailure.FORBIDDEN;
        } else if (status.is5xxServerError()) {
            kind = IdentityClientFailure.UNAVAILABLE;
        } else {
            kind = IdentityClientFailure.REMOTE_FAILURE;
        }
        return new IdentityClientException(kind, "Identity lookup failed", failure);
    }
}
