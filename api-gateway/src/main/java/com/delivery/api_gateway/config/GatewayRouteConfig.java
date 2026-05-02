package com.delivery.api_gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class GatewayRouteConfig {

        private final JwtAuthenticationFilter jwtFilter;

        @Bean
        public RouteLocator customRoutes(RouteLocatorBuilder builder) {
                return builder.routes()
                                // Public auth endpoints (no JWT required)
                                .route("auth-service-public", r -> r.path(
                                                "/api/auth/login",
                                                "/api/auth/register",
                                                "/api/auth/refresh-token",
                                                "/api/auth/logout",
                                                "/api/auth/accounts/email/**")
                                                .uri("http://localhost:8081"))

                                // Protected auth endpoints (JWT required)
                                .route("auth-service-protected", r -> r.path("/api/auth/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri("http://localhost:8081"))

                                .route("user-service", r -> r.path("/api/users/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri("http://localhost:8082"))
                                .route("user-service", r -> r.path("/api/addresses/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri("http://localhost:8082"))

                                .route("restaurant-service", r -> r.path("/api/restaurants/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri("http://localhost:8083"))
                                .route("restaurant-service", r -> r.path("/api/menu-items/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri("http://localhost:8083"))
                                // ✅ Admin endpoints (no JWT) — MUST be before the protected orders route
                                .route("order-service-admin", r -> r.path("/api/orders/admin/**")
                                                .uri("http://localhost:8084"))

                                .route("order-service", r -> r.path("/api/orders/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri("http://localhost:8084"))

                                // ✅ Admin endpoints (no JWT) — MUST be before the protected delivery route
                                .route("delivery-service-admin", r -> r.path("/api/deliveries/admin/**")
                                                .uri("http://localhost:8085"))

                                .route("delivery-service", r -> r.path("/api/deliveries/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri("http://localhost:8085"))

                                .route("shipper-service", r -> r.path("/api/shippers/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri("http://localhost:8086"))

                                .route("notification-service", r -> r.path("/api/notifications/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri("http://localhost:8087"))
                                .route("firebase-service", r -> r.path("/api/firebase/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri("http://localhost:8087"))

                                .route("search-service", r -> r.path("/api/search/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri("http://localhost:8088"))

                                .route("saga-orchestrator", r -> r.path("/api/orchestrator/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri("http://localhost:8089"))

                                .route("tracking-service", r -> r.path("/api/tracking/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri("http://localhost:8090"))
                                .route("tracking-service-ws", r -> r.path("/ws/shipper-locations/**")
                                                .uri("ws://localhost:8090"))

                                .route("match-service", r -> r.path("/api/match/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri("http://localhost:8091"))
                                .route("livestream-service", r -> r.path("/api/livestreams/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri("http://localhost:8092"))

                                .route("settlement-service", r -> r.path("/api/settlement/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri("http://localhost:8095"))

                                .build();
        }
}
