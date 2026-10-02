package com.delivery.web_bff.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import com.delivery.web_bff.infrastructure.auth.HttpAuthGatewayAdapter;
import com.delivery.web_bff.application.api.Ports;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class HttpAuthGatewayTest {
    @Test
    void optionalDeviceNameDoesNotPreventLogin() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://auth.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://auth.test/api/auth/login"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.deviceName").doesNotExist())
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                        "{\"status\":1,\"data\":{\"accessToken\":\"access\",\"refreshToken\":\"refresh\",\"authId\":21,\"email\":\"owner@example.test\",\"role\":\"SHOP_OWNER\"}}", MediaType.APPLICATION_JSON));
        var result = new HttpAuthGatewayAdapter(builder.build()).login(new Ports.LoginCommand("owner@example.test", "password", "SHOP_OWNER", null, "web-bff-test"));
        assertThat(result.principalId()).isEqualTo(21);
        server.verify();
    }

    @Test
    void loginTurnsUpstreamUnauthorizedIntoSafeAuthenticationRejection() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://auth.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://auth.test/api/auth/login"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":0,\"message\":\"upstream detail must not leak\",\"data\":null}"));
        HttpAuthGatewayAdapter gateway = new HttpAuthGatewayAdapter(builder.build());

        assertThatThrownBy(() -> gateway.login(new Ports.LoginCommand(
                "user@example.com", "wrong-password", "USER", "Browser", "device-1")))
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
        HttpAuthGatewayAdapter gateway = new HttpAuthGatewayAdapter(builder.build());

        assertThatThrownBy(() -> gateway.login(new Ports.LoginCommand(
                "user@example.com", "password", "USER", "Browser", "device-1")))
                .isInstanceOf(HttpServerErrorException.ServiceUnavailable.class);
        server.verify();
    }
}
