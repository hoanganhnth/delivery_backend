package com.delivery.platform.http.blocking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

class RestTemplateBlockingHttpExchangeTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsCallerSuppliedHeadersAndReturnsTypedResponse() throws Exception {
        AtomicReference<String> token = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/principal", exchange -> {
            token.set(exchange.getRequestHeaders().getFirst("Internal-Token"));
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Internal-Token", "explicit-secret");
        AtomicInteger interceptorCalls = new AtomicInteger();
        RestTemplate template = new RestTemplate();
        template.getInterceptors().add((request, body, execution) -> {
            interceptorCalls.incrementAndGet();
            return execution.execute(request, body);
        });

        String body = client(template, Duration.ofSeconds(1))
                .get(uri("/principal"), headers, String.class).getBody();

        assertEquals("ok", body);
        assertEquals("explicit-secret", token.get());
        assertEquals(1, interceptorCalls.get());
    }

    @Test
    void refusesToForwardCallerCredentialsAcrossRedirects() throws Exception {
        AtomicInteger redirectedRequests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", uri("/credential-sink").toString());
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/credential-sink", exchange -> {
            redirectedRequests.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Internal-Token", "must-not-follow");

        var response = client().get(uri("/redirect"), headers, String.class);

        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals(0, redirectedRequests.get());
    }

    @Test
    void appliesCallerOwnedReadTimeout() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/slow", exchange -> {
            LockSupport.parkNanos(Duration.ofMillis(300).toNanos());
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        assertThrows(ResourceAccessException.class, () ->
                client(new RestTemplate(), Duration.ofMillis(50))
                        .get(uri("/slow"), HttpHeaders.EMPTY, String.class));
    }

    @Test
    void requiresPositiveCallerOwnedTimeouts() {
        RestTemplate template = new RestTemplate();
        assertThrows(IllegalArgumentException.class, () ->
                new RestTemplateBlockingHttpExchange(
                        template,
                        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build(),
                        Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () ->
                new RestTemplateBlockingHttpExchange(
                        template,
                        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
                        Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () ->
                new RestTemplateBlockingHttpExchange(
                        template, safeHttpClient(), Duration.ofSeconds(-1)));
    }

    private RestTemplateBlockingHttpExchange client() {
        return client(new RestTemplate(), Duration.ofSeconds(1));
    }

    private RestTemplateBlockingHttpExchange client(
            RestTemplate template, Duration readTimeout) {
        return new RestTemplateBlockingHttpExchange(
                template, safeHttpClient(), readTimeout);
    }

    private HttpClient safeHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }
}
