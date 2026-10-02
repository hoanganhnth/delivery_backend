package com.delivery.web_bff.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.delivery.web_bff.application.api.Ports;
import com.delivery.web_bff.application.api.UseCases;
import com.delivery.web_bff.infrastructure.proxy.GatewayProxyAdapter;
import com.delivery.web_bff.domain.session.ApiProxyRejectedException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ApiProxyServiceTest {
    private final UseCases.AccessTokenResolution sessions = mock(UseCases.AccessTokenResolution.class);

    @Test
    void injectsServerBearerAndNeverForwardsBrowserAuthorizationOrCookie() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gateway.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://gateway.test/api/users?page=1"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer server-access"))
                .andExpect(request -> assertThat(request.getHeaders().get(HttpHeaders.COOKIE)).isNull())
                .andRespond(withSuccess("{\"status\":1}", MediaType.APPLICATION_JSON));
        when(sessions.execute("session", null, false)).thenReturn("server-access");
        HttpHeaders browser = new HttpHeaders();
        browser.setBearerAuth("browser-controlled");
        browser.add(HttpHeaders.COOKIE, "evil=true");

        var response = new ProxyForwardingApplicationService(new ApiProxyPolicyApplicationService(), sessions, new GatewayProxyAdapter(builder.build())).execute(new UseCases.ProxyRequest(Ports.HttpVerb.GET,
                "/api/users", "page=1", browser, new byte[0], "session", null));

        assertThat(response.status()).isEqualTo(200);
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).contains("status");
        verify(sessions).execute("session", null, false);
        server.verify();
    }

    @Test
    void mutationsRequireCsrfAndCredentialEndpointsAreRejectedBeforeSessionLookup() {
        RestClient client = RestClient.builder().baseUrl("http://gateway.test").build();
        when(sessions.execute("session", "csrf", true)).thenReturn("server-access");

        assertThatThrownBy(() -> new ProxyForwardingApplicationService(new ApiProxyPolicyApplicationService(), sessions, new GatewayProxyAdapter(client)).execute(new UseCases.ProxyRequest(Ports.HttpVerb.POST,
                "/api/auth/refresh-token", null, new HttpHeaders(), new byte[0], "session", "csrf")))
                .isInstanceOf(ApiProxyRejectedException.class);
    }
}
