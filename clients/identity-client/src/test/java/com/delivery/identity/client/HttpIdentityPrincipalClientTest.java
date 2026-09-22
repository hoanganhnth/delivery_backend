package com.delivery.identity.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.delivery.identity.contracts.IdentityLifecycleStatus;
import com.delivery.identity.contracts.IdentityPrincipal;
import com.delivery.identity.contracts.IdentityRole;
import com.delivery.platform.http.blocking.BlockingHttpExchange;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

class HttpIdentityPrincipalClientTest {

    @Test
    void findsTypedPrincipalUsingExplicitTargetCredential() {
        RecordingExchange exchange = new RecordingExchange(ResponseEntity.ok(new IdentityPrincipal(
                42L, IdentityRole.SHOP_OWNER, IdentityLifecycleStatus.ACTIVE)));

        var principal = client(exchange).findByPrincipalId(42L).orElseThrow();

        assertThat(principal.principalId()).isEqualTo(42L);
        assertThat(exchange.uri).isEqualTo(
                URI.create("http://auth.test/api/auth/internal/principals/42"));
        assertThat(exchange.headers.getFirst("Internal-Token")).isEqualTo("restaurant-secret");
    }

    @Test
    void mapsOnlyNotFoundToEmpty() {
        var client = client(new RecordingExchange(ResponseEntity.notFound().build()));

        assertThat(client.findByPrincipalId(404L)).isEmpty();

        RecordingExchange thrown404 = new RecordingExchange(null);
        thrown404.failure = new RestClientResponseException(
                "not found", 404, "Not Found", HttpHeaders.EMPTY, new byte[0], null);
        assertThat(client(thrown404).findByPrincipalId(404L)).isEmpty();
    }

    @Test
    void classifiesStatusFailuresWithoutRetrying() {
        assertResponseFailure(HttpStatus.BAD_REQUEST, IdentityClientFailure.INVALID_REQUEST);
        assertResponseFailure(HttpStatus.FORBIDDEN, IdentityClientFailure.FORBIDDEN);
        assertResponseFailure(HttpStatus.SERVICE_UNAVAILABLE, IdentityClientFailure.UNAVAILABLE);
        assertResponseFailure(HttpStatus.CONFLICT, IdentityClientFailure.REMOTE_FAILURE);
    }

    @Test
    void classifiesNetworkAndDecodingFailures() {
        assertTransportFailure(
                new ResourceAccessException("timeout"), IdentityClientFailure.UNAVAILABLE);
        assertTransportFailure(
                new RestClientException("malformed JSON"), IdentityClientFailure.REMOTE_FAILURE);
    }

    @Test
    void rejectsMalformedSuccessfulPrincipals() {
        assertInvalidPrincipal(null);
        assertInvalidPrincipal(new IdentityPrincipal(
                41L, IdentityRole.SHOP_OWNER, IdentityLifecycleStatus.ACTIVE));
        assertInvalidPrincipal(new IdentityPrincipal(
                42L, null, IdentityLifecycleStatus.ACTIVE));
        assertInvalidPrincipal(new IdentityPrincipal(42L, IdentityRole.SHOP_OWNER, null));
    }

    @Test
    void rejectsInvalidInputAndConfigurationBeforeNetwork() {
        RecordingExchange exchange = new RecordingExchange(ResponseEntity.ok().build());
        assertThatThrownBy(() -> client(exchange).findByPrincipalId(0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(exchange.calls).isZero();
        assertThatThrownBy(() -> new HttpIdentityPrincipalClient(
                exchange, URI.create("http://auth.test"), " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HttpIdentityPrincipalClient(
                exchange, URI.create("relative"), "secret"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private HttpIdentityPrincipalClient client(BlockingHttpExchange exchange) {
        return new HttpIdentityPrincipalClient(
                exchange, URI.create("http://auth.test"), "restaurant-secret");
    }

    private void assertResponseFailure(
            HttpStatus status, IdentityClientFailure expectedFailure) {
        assertFailure(client(new RecordingExchange(ResponseEntity.status(status).build())), expectedFailure);
    }

    private void assertTransportFailure(
            RestClientException failure, IdentityClientFailure expectedFailure) {
        RecordingExchange exchange = new RecordingExchange(null);
        exchange.failure = failure;
        assertFailure(client(exchange), expectedFailure);
        assertThat(exchange.calls).isEqualTo(1);
    }

    private void assertInvalidPrincipal(IdentityPrincipal principal) {
        assertFailure(
                client(new RecordingExchange(ResponseEntity.ok(principal))),
                IdentityClientFailure.REMOTE_FAILURE);
    }

    private void assertFailure(
            IdentityPrincipalClient client, IdentityClientFailure expectedFailure) {
        assertThatThrownBy(() -> client.findByPrincipalId(42L))
                .isInstanceOf(IdentityClientException.class)
                .satisfies(error -> assertThat(((IdentityClientException) error).kind())
                        .isEqualTo(expectedFailure));
    }

    private static final class RecordingExchange implements BlockingHttpExchange {
        private final ResponseEntity<?> response;
        private RuntimeException failure;
        private URI uri;
        private HttpHeaders headers;
        private int calls;

        private RecordingExchange(ResponseEntity<?> response) {
            this.response = response;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> ResponseEntity<T> get(URI uri, HttpHeaders headers, Class<T> responseType) {
            this.calls++;
            this.uri = uri;
            this.headers = headers;
            if (failure != null) {
                throw failure;
            }
            return (ResponseEntity<T>) response;
        }
    }
}
