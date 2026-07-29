package com.delivery.api_gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import reactor.core.publisher.Mono;

class JwtAuthenticationFilterTest {

    private KeyPair keyPair;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();

        JwtPublicKeyProvider keyProvider = mock(JwtPublicKeyProvider.class);
        when(keyProvider.getPublicKey()).thenReturn(keyPair.getPublic());
        filter = new JwtAuthenticationFilter(keyProvider);
    }

    @Test
    void rejectsAuthenticatedUserWithoutTheRequiredRole() {
        MockServerWebExchange exchange = exchangeWithToken(token("USER"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.apply(new JwtAuthenticationFilter.Config().setRequiredRole("ADMIN"))
                .filter(exchange, request -> {
                    chainCalled.set(true);
                    return Mono.empty();
                })
                .block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(chainCalled).isFalse();
    }

    @Test
    void forwardsAuthenticatedAdminAndReplacesIdentityHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders/admin/cancel-all-pending")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("ADMIN"))
                        .header("X-User-Id", "spoofed")
                        .header("X-Role", "USER"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.apply(new JwtAuthenticationFilter.Config().setRequiredRole("ADMIN"))
                .filter(exchange, request -> {
                    chainCalled.set(true);
                    assertThat(request.getRequest().getHeaders().getFirst("X-User-Id"))
                            .isEqualTo("42");
                    assertThat(request.getRequest().getHeaders().getFirst("X-Role"))
                            .isEqualTo("ADMIN");
                    return Mono.empty();
                })
                .block();

        assertThat(chainCalled).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void acceptsAnyRoleInAnExplicitRoleSet() {
        MockServerWebExchange exchange = exchangeWithToken(token("SHOP_OWNER"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.apply(new JwtAuthenticationFilter.Config().setRequiredRoles("SHOP_OWNER", "ADMIN"))
                .filter(exchange, request -> {
                    chainCalled.set(true);
                    return Mono.empty();
                })
                .block();

        assertThat(chainCalled).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void rejectsSignedTokenWithoutCanonicalIdentityClaims() {
        long now = System.currentTimeMillis();
        String tokenWithoutSubject = Jwts.builder()
                .claim("role", "USER")
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + 60_000))
                .signWith(keyPair.getPrivate(), SignatureAlgorithm.RS256)
                .compact();
        MockServerWebExchange exchange = exchangeWithToken(tokenWithoutSubject);
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.apply(new JwtAuthenticationFilter.Config())
                .filter(exchange, request -> {
                    chainCalled.set(true);
                    return Mono.empty();
                })
                .block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(chainCalled).isFalse();
    }

    private MockServerWebExchange exchangeWithToken(String token) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders/admin/cancel-all-pending")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private String token(String role) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject("42")
                .claim("role", role)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + 60_000))
                .signWith(keyPair.getPrivate(), SignatureAlgorithm.RS256)
                .compact();
    }
}
