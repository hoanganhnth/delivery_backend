package com.delivery.api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class GatewayRouteConfig {

        private final JwtAuthenticationFilter jwtFilter;

        @Value("${app.auth-service.uri:http://localhost:8081}")
        private String authServiceUri;

        @Value("${app.user-service.uri:http://localhost:8082}")
        private String userServiceUri;

        @Value("${app.restaurant-service.uri:http://localhost:8083}")
        private String restaurantServiceUri;

        @Value("${app.order-service.uri:http://localhost:8084}")
        private String orderServiceUri;

        @Value("${app.delivery-service.uri:http://localhost:8085}")
        private String deliveryServiceUri;

        @Value("${app.search-service.uri:http://localhost:8088}")
        private String searchServiceUri;

        @Value("${app.shipper-service.uri:http://localhost:8089}")
        private String shipperServiceUri;

        @Value("${app.notification-service.uri:http://localhost:8091}")
        private String notificationServiceUri;

        @Value("${app.saga-orchestrator-service.uri:http://localhost:8095}")
        private String sagaOrchestratorUri;

        @Value("${app.tracking-service.uri:http://localhost:8093}")
        private String trackingServiceUri;

        @Value("${app.tracking-service.ws-uri:ws://localhost:8093}")
        private String trackingServiceWsUri;

        @Value("${app.match-service.uri:http://localhost:8092}")
        private String matchServiceUri;

        @Value("${app.livestream-service.uri:http://localhost:8094}")
        private String livestreamServiceUri;

        @Value("${app.settlement-service.uri:http://localhost:8090}")
        private String settlementServiceUri;

        @Value("${app.promotion-service.uri:http://localhost:8096}")
        private String promotionServiceUri;

        @Value("${app.analytics-service.uri:http://localhost:8097}")
        private String analyticsServiceUri;

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
                                                .uri(authServiceUri))
                                .route("search-service-public", r -> r.path("/api/search/**")
                                                .uri(searchServiceUri))

                                // Protected auth endpoints (JWT required)
                                .route("auth-service-protected", r -> r.path("/api/auth/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri(authServiceUri))

                                .route("user-service", r -> r.path("/api/users/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri(userServiceUri))
                                .route("user-address-service", r -> r.path("/api/addresses/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri(userServiceUri))

                                .route("restaurant-service", r -> r.path("/api/restaurants/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri(restaurantServiceUri))
                                .route("restaurant-menu-service", r -> r.path("/api/menu-items/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri(restaurantServiceUri))
                                // ✅ Admin endpoints (no JWT) — MUST be before the protected orders route
                                .route("order-service-admin", r -> r.path("/api/orders/admin/**")
                                                .uri(orderServiceUri))

                                .route("order-service", r -> r.path("/api/orders/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri(orderServiceUri))

                                // ✅ Analytics Dashboard (protected with JWT) → analytics-service
                                .route("analytics-dashboard", r -> r.path("/api/dashboard/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri(analyticsServiceUri))
                                .route("analytics-service", r -> r.path("/api/analytics/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri(analyticsServiceUri))

                                // ✅ Admin endpoints (no JWT) — MUST be before the protected delivery route
                                .route("delivery-service-admin", r -> r.path("/api/deliveries/admin/**")
                                                .uri(deliveryServiceUri))

                                .route("delivery-service", r -> r.path("/api/deliveries/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri(deliveryServiceUri))

                                .route("shipper-service", r -> r.path("/api/shippers/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri(shipperServiceUri))

                                .route("notification-service", r -> r.path("/api/notifications/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri(notificationServiceUri))
                                .route("firebase-service", r -> r.path("/api/firebase/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri(notificationServiceUri))

                                .route("saga-orchestrator", r -> r.path("/api/orchestrator/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri(sagaOrchestratorUri))

                                .route("tracking-service", r -> r.path("/api/tracking/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri(trackingServiceUri))
                                .route("tracking-service-ws", r -> r.path("/ws/shipper-locations/**")
                                                .uri(trackingServiceWsUri))

                                .route("match-service", r -> r.path("/api/match/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri(matchServiceUri))
                                .route("livestream-service", r -> r.path("/api/livestreams/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri(livestreamServiceUri))

                                .route("settlement-service", r -> r.path("/api/settlement/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri(settlementServiceUri))

                                .route("promotion-service", r -> r.path("/api/promotions/**")
                                                .filters(f -> f.filter(
                                                                jwtFilter.apply(new JwtAuthenticationFilter.Config())))
                                                .uri(promotionServiceUri))

                                .build();
        }
}
