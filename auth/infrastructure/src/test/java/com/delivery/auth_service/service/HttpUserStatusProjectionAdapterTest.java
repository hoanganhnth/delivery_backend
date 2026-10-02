package com.delivery.auth_service.service;

import com.delivery.auth_service.config.UserServiceConfig;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import static org.assertj.core.api.Assertions.*;

class HttpUserStatusProjectionAdapterTest {
    @Test void preservesInternalWireContractAndRejectsUnconfirmedProjectionReplies() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var responseCode=new AtomicInteger(200);
        var response=new AtomicReference<>("{\"status\":1}");
        var request=new AtomicReference<String>(); var secret=new AtomicReference<String>();
        server.createContext("/api/internal/users/7/block-status",exchange -> {
            secret.set(exchange.getRequestHeaders().getFirst("Internal-Token"));
            request.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            var body=response.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(responseCode.get(),body.length);
            try(var out=exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try {
            var config=new UserServiceConfig();config.setUrl("http://127.0.0.1:"+server.getAddress().getPort());config.setInternalSecret("fixture-secret");
            var adapter=new HttpUserStatusProjectionAdapter(config,new RestTemplate(),null);
            adapter.synchronize(7L,99L,"review",true);
            var json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(request.get());
            assertThat(json.get("adminId").asLong()).isEqualTo(99L);
            assertThat(json.get("blocked").asBoolean()).isTrue();
            assertThat(json.get("reason").asText()).isEqualTo("review");
            assertThat(secret.get()).isEqualTo("fixture-secret");
            adapter.synchronize(7L,99L,null,false);
            json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(request.get());
            assertThat(json.get("blocked").asBoolean()).isFalse();assertThat(json.has("reason")).isFalse();
            for(String reply:new String[]{"{\"status\":0}","null"}) {
                response.set(reply);
                assertThatThrownBy(() -> adapter.synchronize(7L,99L,"review",true))
                        .hasMessage("User profile status synchronization was rejected");
            }
            responseCode.set(503);response.set("{}");
            assertThatThrownBy(() -> adapter.synchronize(7L,99L,"review",true))
                    .hasMessage("Failed to synchronize user profile block state")
                    .hasCauseInstanceOf(org.springframework.web.client.RestClientException.class);
            config.setInternalSecret(" ");
            assertThatThrownBy(() -> adapter.synchronize(7L,99L,"review",true))
                    .hasMessage("INTERNAL_SECRET is required for auth/user linkage");
        } finally { server.stop(0); }
    }
}
