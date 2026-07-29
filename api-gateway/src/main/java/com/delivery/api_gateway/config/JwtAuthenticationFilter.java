package com.delivery.api_gateway.config;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
@Component
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final JwtPublicKeyProvider keyProvider;

    public static class Config {
        private Set<String> requiredRoles = Set.of();

        public String getRequiredRole() {
            return requiredRoles.size() == 1 ? requiredRoles.iterator().next() : null;
        }

        public Set<String> getRequiredRoles() {
            return requiredRoles;
        }

        public Config setRequiredRole(String requiredRole) {
            this.requiredRoles = requiredRole == null ? Set.of() : Set.of(requiredRole);
            return this;
        }

        public Config setRequiredRoles(String... roles) {
            this.requiredRoles = roles == null
                    ? Set.of()
                    : new LinkedHashSet<>(Arrays.asList(roles));
            return this;
        }
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

                if (userId == null || userId.isBlank() || role == null || role.isBlank()) {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }

                if (!config.getRequiredRoles().isEmpty()
                        && !config.getRequiredRoles().contains(role)) {
                    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                    return exchange.getResponse().setComplete();
                }

                exchange = exchange.mutate()
                        .request(r -> r.headers(headers -> {
                            headers.remove("X-User-Id");
                            headers.remove("X-Role");
                            headers.add("X-User-Id", userId);
                            headers.add("X-Role", role);
                        }))
                        .build();

                log.debug("Authenticated userId={}, role={}", userId, role);

            } catch (JwtException e) {
                log.warn("Rejected invalid JWT: {}", e.getMessage());
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            return chain.filter(exchange);
        };
    }
}
