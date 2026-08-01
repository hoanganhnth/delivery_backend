package com.delivery.user_service.service;

import com.delivery.user_service.config.AuthServiceConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AuthRegistrationClientTest {
    @Test
    void authenticatesResolveAndCompleteWithoutSendingClientOwnedIdentity() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        AuthServiceConfig config = new AuthServiceConfig();
        config.setUrl("http://auth-service:8081");
        config.setInternalSecret("service-secret");
        AuthRegistrationClient client = new AuthRegistrationClient(restTemplate, config);

        server.expect(once(), requestTo(config.getResolveRegistrationUrl()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Internal-Token", "service-secret"))
                .andExpect(jsonPath("$.provisioningToken").value("opaque-handoff"))
                .andRespond(withSuccess("""
                        {"status":1,"message":"ok","data":{
                          "authId":11,"email":"user@example.com","role":"USER"}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(config.getCompleteRegistrationUrl()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Internal-Token", "service-secret"))
                .andExpect(jsonPath("$.provisioningToken").value("opaque-handoff"))
                .andExpect(jsonPath("$.userId").value(7))
                .andRespond(withSuccess(
                        "{\"status\":1,\"message\":\"ok\",\"data\":null}",
                        MediaType.APPLICATION_JSON));

        var identity = client.resolve("opaque-handoff");
        client.complete("opaque-handoff", 7L);

        assertThat(identity.getAuthId()).isEqualTo(11L);
        assertThat(identity.getEmail()).isEqualTo("user@example.com");
        assertThat(identity.getRole()).isEqualTo("USER");
        server.verify();
    }
}
