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
                .route("user-service", r -> r.path("/api/users/**")
                    .filters(f -> f.filter(jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                    .uri("http://localhost:8084"))

                .route("auth-service", r -> r.path("/api/auth/**")
                    .uri("http://localhost:8081"))

                .route("orchestrator", r -> r.path("/api/orchestrator/**")
                    .uri("http://localhost:8080")) // gọi chính nó
                .build();
    }
}

