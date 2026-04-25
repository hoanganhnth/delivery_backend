package com.delivery.api_gateway.config;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

@Component
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    private final JwtPublicKeyProvider keyProvider;

    public static class Config {
    }

    public JwtAuthenticationFilter(JwtPublicKeyProvider keyProvider) {
        super(Config.class);
        this.keyProvider = keyProvider;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            String token = authHeader.substring(7);

            try {
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(keyProvider.getPublicKey())
                        .build()
                        .parseClaimsJws(token)
                        .getBody();
                String userId = claims.getSubject();
                String role = claims.get("role", String.class);
                exchange = exchange.mutate()
                        .request(r -> r.headers(headers -> {
                            headers.remove("X-User-Id");
                            headers.remove("X-Role");
                            headers.add("X-User-Id", userId);
                            headers.add("X-Role", role);
                        }))
                        .build();

                System.out.println("✅ Authenticated userId=" + userId + ", role=" + role);

            } catch (JwtException e) {
                System.out.println("❌ Invalid JWT: " + e.getMessage());
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            return chain.filter(exchange);
        };
    }
}
