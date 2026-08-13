package com.delivery.api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration
public class GatewayRouteConfig {

        private final String authServiceUri;
        private final String userServiceUri;
        private final String restaurantServiceUri;
        private final String orderServiceUri;
        private final String deliveryServiceUri;
        private final String searchServiceUri;
        private final String shipperServiceUri;
        private final String notificationServiceUri;
        private final String trackingServiceUri;
        private final String trackingServiceWsUri;
        private final String livestreamServiceUri;
        private final String settlementServiceUri;
        private final String promotionServiceUri;
        private final String analyticsServiceUri;
        private final String flashsaleServiceUri;

        public GatewayRouteConfig(
                        @Value("${app.auth-service.uri:lb://auth-service}") String authServiceUri,
                        @Value("${app.user-service.uri:lb://user-service}") String userServiceUri,
                        @Value("${app.restaurant-service.uri:lb://restaurant-service}") String restaurantServiceUri,
                        @Value("${app.order-service.uri:lb://order-service}") String orderServiceUri,
                        @Value("${app.delivery-service.uri:lb://delivery-service}") String deliveryServiceUri,
                        @Value("${app.search-service.uri:lb://search-service}") String searchServiceUri,
                        @Value("${app.shipper-service.uri:lb://shipper-service}") String shipperServiceUri,
                        @Value("${app.notification-service.uri:lb://notification-service}") String notificationServiceUri,
                        @Value("${app.tracking-service.uri:lb://tracking-service}") String trackingServiceUri,
                        @Value("${app.tracking-service.ws-uri:lb:ws://tracking-service}") String trackingServiceWsUri,
                        @Value("${app.livestream-service.uri:lb://livestream-service}") String livestreamServiceUri,
                        @Value("${app.settlement-service.uri:lb://settlement-service}") String settlementServiceUri,
                        @Value("${app.promotion-service.uri:lb://promotion-service}") String promotionServiceUri,
                        @Value("${app.analytics-service.uri:lb://analytics-service}") String analyticsServiceUri,
                        @Value("${app.flashsale-service.uri:lb://flashsale-service}") String flashsaleServiceUri) {
                this.authServiceUri = authServiceUri;
                this.userServiceUri = userServiceUri;
                this.restaurantServiceUri = restaurantServiceUri;
                this.orderServiceUri = orderServiceUri;
                this.deliveryServiceUri = deliveryServiceUri;
                this.searchServiceUri = searchServiceUri;
                this.shipperServiceUri = shipperServiceUri;
                this.notificationServiceUri = notificationServiceUri;
                this.trackingServiceUri = trackingServiceUri;
                this.trackingServiceWsUri = trackingServiceWsUri;
                this.livestreamServiceUri = livestreamServiceUri;
                this.settlementServiceUri = settlementServiceUri;
                this.promotionServiceUri = promotionServiceUri;
                this.analyticsServiceUri = analyticsServiceUri;
                this.flashsaleServiceUri = flashsaleServiceUri;
        }

        @Bean
        public RouteLocator customRoutes(RouteLocatorBuilder builder) {
                return builder.routes()
                                // Public auth endpoints (no JWT required)
                                .route("auth-service-jwks", r -> r.path("/.well-known/jwks.json")
                                                .and().method(HttpMethod.GET)
                                                .uri(authServiceUri))
                                .route("auth-service-public", r -> r.path(
                                                "/api/auth/login",
                                                "/api/auth/register",
                                                "/api/auth/social-login",
                                                "/api/auth/refresh-token",
                                                "/api/auth/logout",
                                                "/api/auth/forgot-password",
                                                "/api/auth/reset-password",
                                                "/api/auth/email-verification/request",
                                                "/api/auth/email-verification/confirm")
                                                .and().method(HttpMethod.POST)
                                                .uri(authServiceUri))
                                .route("search-service-public", r -> r.path(
                                                "/api/search/restaurants",
                                                "/api/search/dishes")
                                                .and().method(HttpMethod.GET)
                                                .uri(searchServiceUri))
                                .route("user-service-registration", r -> r.path(
                                                "/api/users/registrations")
                                                .and().method(HttpMethod.POST)
                                                .uri(userServiceUri))

                                // Protected auth endpoints (JWT required)
                                .route("auth-service-admin", r -> r.path(
                                                "/api/auth/admin/accounts/{id:[0-9]+}/block",
                                                "/api/auth/admin/accounts/{id:[0-9]+}/unblock")
                                                .and().method(HttpMethod.POST)
                                                .uri(authServiceUri))
                                .route("auth-service-account-admin", r -> r.path("/api/auth/accounts/{id:[0-9]+}")
                                                .and().method(HttpMethod.GET)
                                                .uri(authServiceUri))
                                .route("auth-service-protected", r -> r.path("/api/auth/sessions")
                                                .and().method(HttpMethod.GET)
                                                .uri(authServiceUri))
                                .route("auth-service-session-revoke", r -> r.path("/api/auth/sessions/{deviceId}")
                                                .and().method(HttpMethod.DELETE)
                                                .uri(authServiceUri))

                                // POST /api/users and GET /by-auth/** remain internal linkage APIs.
                                // Only the exact /registrations handoff path is public above.
                                .route("user-service-current", r -> r.path("/api/users")
                                                .and().method(HttpMethod.GET, HttpMethod.PUT)
                                                .uri(userServiceUri))
                                .route("user-service-admin-read", r -> r.path(
                                                "/api/users/admin/statistics",
                                                "/api/users/admin/all")
                                                .and().method(HttpMethod.GET)
                                                .uri(userServiceUri))
                                .route("user-address-service-read", r -> r.path(
                                                "/api/addresses/users/{userId:[0-9]+}/addresses",
                                                "/api/addresses/{id:[0-9]+}")
                                                .and().method(HttpMethod.GET)
                                                .uri(userServiceUri))
                                .route("user-address-service-create", r -> r.path(
                                                "/api/addresses/users/{userId:[0-9]+}/addresses")
                                                .and().method(HttpMethod.POST)
                                                .uri(userServiceUri))
                                .route("user-address-service-update", r -> r.path(
                                                "/api/addresses/{id:[0-9]+}")
                                                .and().method(HttpMethod.PUT)
                                                .uri(userServiceUri))
                                .route("user-address-service-default", r -> r.path(
                                                "/api/addresses/{id:[0-9]+}/default")
                                                .and().method(HttpMethod.PATCH)
                                                .uri(userServiceUri))
                                .route("user-address-service-delete", r -> r.path(
                                                "/api/addresses/{id:[0-9]+}")
                                                .and().method(HttpMethod.DELETE)
                                                .uri(userServiceUri))

                                // Public catalog is read-only. Internal validation, creator lookup,
                                // cache/location utilities and admin mutations are deliberately not
                                // covered by these routes.
                                .route("restaurant-catalog-public", r -> r.path(
                                                "/api/restaurants",
                                                "/api/restaurants/search",
                                                "/api/restaurants/{id:[0-9]+}",
                                                "/api/restaurants/{restaurantId:[0-9]+}/ratings")
                                                .and().method(HttpMethod.GET)
                                                .uri(restaurantServiceUri))
                                .route("restaurant-menu-public", r -> r.path(
                                                "/api/menu-items/restaurant/{restaurantId:[0-9]+}",
                                                "/api/menu-items/restaurant/{restaurantId:[0-9]+}/available")
                                                .and().method(HttpMethod.GET)
                                                .uri(restaurantServiceUri))
                                .route("restaurant-admin-ratings-read", r -> r.path("/api/restaurants/admin/ratings")
                                                .and().method(HttpMethod.GET)
                                                .uri(restaurantServiceUri))
                                .route("restaurant-admin-ratings-update", r -> r.path(
                                                "/api/restaurants/admin/ratings/{id:[0-9]+}/status")
                                                .and().method(HttpMethod.PUT)
                                                .uri(restaurantServiceUri))
                                .route("restaurant-order-actions", r -> r.path(
                                                "/api/restaurants/orders/{orderId:[0-9]+}/confirm",
                                                "/api/restaurants/orders/{orderId:[0-9]+}/reject")
                                                .and().method(HttpMethod.POST)
                                                .uri(restaurantServiceUri))
                                .route("restaurant-owner-self", r -> r.path(
                                                "/api/restaurants/my-restaurants")
                                                .and().method(HttpMethod.GET)
                                                .uri(restaurantServiceUri))
                                .route("restaurant-customer-ratings-self", r -> r.path(
                                                "/api/restaurants/me/ratings")
                                                .and().method(HttpMethod.GET)
                                                .uri(restaurantServiceUri))
                                .route("restaurant-rating-submit", r -> r.path(
                                                "/api/restaurants/{restaurantId:[0-9]+}/ratings")
                                                .and().method(HttpMethod.POST)
                                                .uri(restaurantServiceUri))
                                .route("restaurant-create", r -> r.path("/api/restaurants")
                                                .and().method(HttpMethod.POST)
                                                .uri(restaurantServiceUri))
                                .route("restaurant-update-delete", r -> r.path("/api/restaurants/{id:[0-9]+}")
                                                .and().method(HttpMethod.PUT, HttpMethod.DELETE)
                                                .uri(restaurantServiceUri))
                                .route("restaurant-menu-self", r -> r.path("/api/menu-items/my-menu-items")
                                                .and().method(HttpMethod.GET)
                                                .uri(restaurantServiceUri))
                                .route("restaurant-menu-create", r -> r.path("/api/menu-items")
                                                .and().method(HttpMethod.POST)
                                                .uri(restaurantServiceUri))
                                .route("restaurant-menu-update-delete", r -> r.path("/api/menu-items/{id:[0-9]+}")
                                                .and().method(HttpMethod.PUT, HttpMethod.DELETE)
                                                .uri(restaurantServiceUri))
                                // Admin endpoints must be authenticated before the broader orders route.
                                // Enforce the edge role here; order-service must also keep a
                                // defense-in-depth role check for direct network access.
                                .route("order-service-admin-queries", r -> r.path(
                                                "/api/orders/all",
                                                "/api/orders/status/{status}")
                                                .and().method(HttpMethod.GET)
                                                .uri(orderServiceUri))
                                .route("order-service-create", r -> r.path(
                                                "/api/orders",
                                                "/api/orders/checkout-preview")
                                                .and().method(HttpMethod.POST)
                                                .uri(orderServiceUri))
                                .route("order-service-read", r -> r.path(
                                                "/api/orders/{id:[0-9]+}")
                                                .and().method(HttpMethod.GET)
                                                .uri(orderServiceUri))
                                .route("order-service-customer-self", r -> r.path(
                                                "/api/orders/my-orders")
                                                .and().method(HttpMethod.GET)
                                                .uri(orderServiceUri))
                                .route("order-service-restaurant-self", r -> r.path(
                                                "/api/orders/my-restaurant-orders")
                                                .and().method(HttpMethod.GET)
                                                .uri(orderServiceUri))
                                .route("order-service-cancel", r -> r.path("/api/orders/{id:[0-9]+}/cancel")
                                                .and().method(HttpMethod.PUT)
                                                .uri(orderServiceUri))

                                // Delivery mutations are shipper business commands. The service
                                // repeats ownership/role checks for defense in depth.
                                .route("delivery-service-shipper-actions", r -> r.path(
                                                "/api/deliveries/accept",
                                                "/api/deliveries/cancel-assignment")
                                                .and().method(HttpMethod.POST)
                                                .uri(deliveryServiceUri))
                                .route("delivery-service-status", r -> r.path("/api/deliveries/{id:[0-9]+}/status")
                                                .and().method(HttpMethod.PUT)
                                                .uri(deliveryServiceUri))
                                .route("delivery-service-read", r -> r.path(
                                                "/api/deliveries/{id:[0-9]+}",
                                                "/api/deliveries/order/{orderId:[0-9]+}")
                                                .and().method(HttpMethod.GET)
                                                .uri(deliveryServiceUri))
                                .route("delivery-service-current-offer", r -> r.path(
                                                "/api/deliveries/offers/current")
                                                .and().method(HttpMethod.GET)
                                                .uri(deliveryServiceUri))
                                .route("delivery-service-shipper-read", r -> r.path(
                                                "/api/deliveries/shipper/{shipperId:[0-9]+}",
                                                "/api/deliveries/shipper/{shipperId:[0-9]+}/active")
                                                .and().method(HttpMethod.GET)
                                                .uri(deliveryServiceUri))

                                .route("shipper-service-self-create", r -> r.path("/api/shippers")
                                                .and().method(HttpMethod.POST)
                                                .uri(shipperServiceUri))
                                .route("shipper-service-self", r -> r.path(
                                                "/api/shippers/my-profile",
                                                "/api/shippers/me/ratings")
                                                .and().method(HttpMethod.GET)
                                                .uri(shipperServiceUri))
                                .route("shipper-service-self-update", r -> r.path("/api/shippers")
                                                .and().method(HttpMethod.PUT)
                                                .uri(shipperServiceUri))
                                .route("shipper-service-online", r -> r.path("/api/shippers/online-status")
                                                .and().method(HttpMethod.PATCH)
                                                .uri(shipperServiceUri))
                                .route("shipper-service-admin", r -> r.path(
                                                "/api/shippers",
                                                "/api/shippers/online",
                                                "/api/shippers/{id:[0-9]+}")
                                                .and().method(HttpMethod.GET)
                                                .uri(shipperServiceUri))

                                // Client notification routes are method-scoped so the internal
                                // POST /send endpoint cannot be reached through the public edge.
                                .route("notification-service-read", r -> r.path(
                                                "/api/notifications/user/{userId:[0-9]+}",
                                                "/api/notifications/unread",
                                                "/api/notifications/unread-count",
                                                "/api/notifications/{id:[0-9]+}")
                                                .and().method(HttpMethod.GET)
                                                .uri(notificationServiceUri))
                                .route("notification-service-update", r -> r.path(
                                                "/api/notifications/{id:[0-9]+}/read",
                                                "/api/notifications/mark-all-read")
                                                .and().method(HttpMethod.PUT)
                                                .uri(notificationServiceUri))
                                .route("notification-service-delete", r -> r.path(
                                                "/api/notifications/{id:[0-9]+}")
                                                .and().method(HttpMethod.DELETE)
                                                .uri(notificationServiceUri))
                                .route("firebase-service", r -> r.path(
                                                "/api/firebase/register-token",
                                                "/api/firebase/unregister-token")
                                                .and().method(HttpMethod.POST)
                                                .uri(notificationServiceUri))

                                // A shipper may publish only through the two self-identity
                                // controller methods. Fleet queries and busy-state mutations are
                                // internal and intentionally have no Gateway route.
                                .route("tracking-service-shipper", r -> r.path(
                                                "/api/tracking/shipper-locations/update",
                                                "/api/tracking/shipper-locations/offline")
                                                .and().method(HttpMethod.POST)
                                                .uri(trackingServiceUri))
                                // Arbitrary shipper-point reads are intentionally hidden. Customer
                                // tracking uses the participant-authorized raw WebSocket contract.
                                .route("tracking-service-ws", r -> r.path("/ws/shipper-locations")
                                                .uri(trackingServiceWsUri))

                                // COD-first MVP: only customer-owned refund status and audited
                                // admin read surfaces are reachable. Self-service balance,
                                // withdrawal, manual deposit and fake/online payment APIs stay
                                // hidden until entity ownership and provider-backed money movement
                                // are proven end to end.
                                .route("settlement-service-customer-refund-read", r -> r.path(
                                                "/api/settlement/refunds/my")
                                                .and().method(HttpMethod.GET)
                                                .uri(settlementServiceUri))
                                .route("settlement-service-admin-read", r -> r.path(
                                                "/api/settlement/admin/balances",
                                                "/api/settlement/admin/transactions",
                                                "/api/settlement/admin/transactions/pending",
                                                "/api/settlement/admin/revenue",
                                                "/api/settlement/admin/refunds",
                                                "/api/settlement/admin/refunds/{refundId:[0-9a-fA-F-]+}")
                                                .and().method(HttpMethod.GET)
                                                .uri(settlementServiceUri))

                                .route("promotion-service-user-collect", r -> r
                                                .path("/api/promotions/collect/{code}")
                                                .and().method(HttpMethod.POST)
                                                .uri(promotionServiceUri))
                                .route("promotion-service-user-vouchers", r -> r
                                                .path("/api/promotions/my-vouchers")
                                                .and().method(HttpMethod.GET)
                                                .uri(promotionServiceUri))
                                // Merchant voucher creation stays hidden until restaurantId is
                                // explicit and restaurant ownership is verified. ownerId is not a
                                // valid substitute for restaurantId.
                                .route("promotion-service-merchant-list", r -> r
                                                .path("/api/promotions/merchant")
                                                .and().method(HttpMethod.GET)
                                                .uri(promotionServiceUri))
                                .route("promotion-service-admin-create", r -> r
                                                .path("/api/promotions/platform")
                                                .and().method(HttpMethod.POST)
                                                .uri(promotionServiceUri))
                                .route("promotion-service-admin-list", r -> r
                                                .path("/api/promotions/admin")
                                                .and().method(HttpMethod.GET)
                                                .uri(promotionServiceUri))
                                .route("promotion-service-admin-delete", r -> r
                                                .path("/api/promotions/{id:[0-9]+}")
                                                .and().method(HttpMethod.DELETE)
                                                .uri(promotionServiceUri))

                                // Internal stock reservation is called service-to-service and is not
                                // reachable through the public Gateway.
                                .route("flashsale-public", r -> r.path(
                                                "/api/flashsales/public/campaigns",
                                                "/api/flashsales/public/campaigns/{campaignId:[0-9]+}/items")
                                                .and().method(HttpMethod.GET)
                                                .uri(flashsaleServiceUri))
                                .route("flashsale-admin-read", r -> r.path(
                                                "/api/flashsales/admin/campaigns",
                                                "/api/flashsales/admin/campaigns/{id:[0-9]+}/items")
                                                .and().method(HttpMethod.GET)
                                                .uri(flashsaleServiceUri))
                                .route("flashsale-admin-create", r -> r.path(
                                                "/api/flashsales/admin/campaigns")
                                                .and().method(HttpMethod.POST)
                                                .uri(flashsaleServiceUri))
                                .route("flashsale-admin-update", r -> r.path(
                                                "/api/flashsales/admin/campaigns/{id:[0-9]+}/status",
                                                "/api/flashsales/admin/items/{id:[0-9]+}/approve")
                                                .and().method(HttpMethod.PUT)
                                                .uri(flashsaleServiceUri))

                                .build();
        }
}
