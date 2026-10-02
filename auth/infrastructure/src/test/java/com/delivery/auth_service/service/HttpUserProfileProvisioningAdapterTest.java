package com.delivery.auth_service.service;

import com.delivery.auth.application.DefaultUserProfileProvisioningUseCase;
import com.delivery.auth.domain.model.AuthAccount;
import com.delivery.auth_service.config.UserServiceConfig;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import static org.assertj.core.api.Assertions.*;

class HttpUserProfileProvisioningAdapterTest {
    @Test void exchangesTheExistingWireRequestAndBindsOnlyAConfirmedIdentity() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        java.util.concurrent.atomic.AtomicInteger responseCode=new java.util.concurrent.atomic.AtomicInteger(200);
        AtomicReference<String> request=new AtomicReference<>(), secret=new AtomicReference<>();
        AtomicReference<String> response=new AtomicReference<>("{\"status\":1,\"message\":\"ok\",\"data\":{\"id\":11,\"authId\":7,\"email\":\"USER@example.com\",\"role\":\"USER\"}}");
        server.createContext("/api/users",exchange -> {
            secret.set(exchange.getRequestHeaders().getFirst("Internal-Token"));
            request.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] bytes=response.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(responseCode.get(),bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.start();
        try {
            UserServiceConfig config=new UserServiceConfig(); config.setUrl("http://127.0.0.1:"+server.getAddress().getPort());config.setInternalSecret("fixture-secret");
            var core=new DefaultUserProfileProvisioningUseCase(new HttpUserProfileProvisioningAdapter(config,new RestTemplate(),null));
            assertThat(core.provision(account()).userId()).isEqualTo(11L);
            var json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(request.get());
            assertThat(json.get("authId").asLong()).isEqualTo(7L);assertThat(json.get("principalId").asLong()).isEqualTo(7L);
            assertThat(json.get("email").asText()).isEqualTo("user@example.com");assertThat(json.get("role").asText()).isEqualTo("USER");
            assertThat(secret.get()).isEqualTo("fixture-secret");
            response.set("{\"status\":1,\"data\":{\"id\":11,\"authId\":8,\"email\":\"user@example.com\",\"role\":\"USER\"}}");
            assertThatThrownBy(() -> core.provision(account())).hasMessage("User service returned a conflicting provisioning identity");
            responseCode.set(503);
            assertThatThrownBy(() -> core.provision(account())).hasMessage("Failed to provision user profile")
                    .hasCauseInstanceOf(org.springframework.web.client.RestClientException.class);
            config.setInternalSecret(" ");
            assertThatThrownBy(() -> core.provision(account())).hasMessage("INTERNAL_SECRET is required for auth/user linkage");
        } finally { server.stop(0); }
    }
    static AuthAccount account() {
        return new AuthAccount(7L,null,AuthAccount.LifecycleStatus.PENDING_PROFILE,0L,"user@example.com","hash",AuthAccount.Role.USER,
                true,true,null,false,0L,null,null,0,null,false,null,null,0L,null,null,null);
    }
}
