package com.delivery.web_bff.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class HttpAuthGatewayTest {
    @Test
    void loginTurnsUpstreamUnauthorizedIntoSafeAuthenticationRejection() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://auth.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://auth.test/api/auth/login"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":0,\"message\":\"upstream detail must not leak\",\"data\":null}"));
        HttpAuthGateway gateway = new HttpAuthGateway(builder.build());

        assertThatThrownBy(() -> gateway.login(new AuthGateway.LoginCommand(
                "user@example.com", "wrong-password", "USER", "device-1", "Browser")))
                .satisfies(exception -> {
                    assertThat(exception.getClass().getSimpleName()).isEqualTo("AuthenticationRejectedException");
                    assertThat(exception).hasMessage("Invalid email or password");
                });
        server.verify();
    }

    @Test
    void loginDoesNotMisclassifyUpstreamServerFailureAsCredentialRejection() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://auth.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://auth.test/api/auth/login"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        HttpAuthGateway gateway = new HttpAuthGateway(builder.build());

        assertThatThrownBy(() -> gateway.login(new AuthGateway.LoginCommand(
                "user@example.com", "password", "USER", "device-1", "Browser")))
                .isInstanceOf(HttpServerErrorException.ServiceUnavailable.class);
        server.verify();
    }
}
