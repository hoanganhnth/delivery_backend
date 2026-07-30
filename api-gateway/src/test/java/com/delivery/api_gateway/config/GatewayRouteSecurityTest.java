package com.delivery.api_gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.delivery.api_gateway.TestJwtPublicKeyProperties;

import reactor.core.publisher.Mono;

@SpringBootTest
class GatewayRouteSecurityTest {

    @Autowired
    private RouteLocator routeLocator;

    @DynamicPropertySource
    static void jwtKeyProperties(DynamicPropertyRegistry registry) {
        TestJwtPublicKeyProperties.register(registry);
    }

    @Test
    void sensitiveRoutesAreNotExposedAsPublicUserRoutes() {
        Map<String, Route> routes = routeLocator.getRoutes()
                .collectMap(Route::getId)
                .block();

        assertThat(routes).isNotNull();
        assertThat(routes).doesNotContainKey("match-service");

        Route authPublic = routes.get("auth-service-public");
        assertThat(authPublic).isNotNull();
        assertThat(authPublic.getPredicate().toString())
                .contains("/api/auth/social-login")
                .doesNotContain("/api/auth/accounts/email");

        assertThat(routes.get("flashsale-public").getFilters()).isEmpty();
        assertThat(routes).doesNotContainKey("flashsale-merchant");
        assertThat(routes.get("flashsale-admin-read").getFilters()).isNotEmpty();
        assertThat(routes.get("flashsale-admin-create").getFilters()).isNotEmpty();
        assertThat(routes.get("flashsale-admin-update").getFilters()).isNotEmpty();
        assertThat(matches(routes, HttpMethod.POST, "/api/flashsales/internal/reserve")).isFalse();
        assertThat(matches(routes, HttpMethod.POST, "/api/flashsales/merchant/items")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/flashsales/public/future-endpoint")).isFalse();
        assertThat(matches(routes, HttpMethod.DELETE, "/api/flashsales/admin/campaigns/42")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/auth/login")).isFalse();
        assertThat(matches(routes, HttpMethod.POST, "/api/auth/login")).isTrue();
        assertThat(matches(routes, HttpMethod.GET, "/api/auth/sessions")).isTrue();
        assertThat(matches(routes, HttpMethod.POST, "/api/auth/sessions")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/auth/unknown")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/orchestrator/sagas/42")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/actuator/health")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/actuator/health/liveness")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/actuator/health/readiness")).isFalse();
    }

    @Test
    void adminMaintenanceRoutesRequireTheJwtFilter() {
        Map<String, Route> routes = routeLocator.getRoutes()
                .collectMap(Route::getId)
                .block();

        assertThat(routes).isNotNull();
        assertThat(routes).doesNotContainKeys("order-service-admin", "delivery-service-admin");
        assertThat(routes).doesNotContainKey("auth-service-admin-list");
        assertThat(routes.get("auth-service-admin").getFilters()).isNotEmpty();
        assertThat(routes.get("auth-service-account-admin").getFilters()).isNotEmpty();
        assertThat(matches(routes, HttpMethod.GET, "/api/auth/admin/accounts")).isFalse();
        assertThat(matches(routes, HttpMethod.POST, "/api/orders/admin/cancel-all-pending")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/orders/admin/future-endpoint")).isFalse();
    }

    @Test
    void internalNotificationSendIsNotRoutedButClientReadsAreRouted() {
        Map<String, Route> routes = routes();

        assertThat(matches(routes, HttpMethod.POST, "/api/notifications/send")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/notifications/unread")).isTrue();
        assertThat(matches(routes, HttpMethod.GET, "/api/notifications/42")).isTrue();
        assertThat(matches(routes, HttpMethod.PUT, "/api/notifications/42/read")).isTrue();
        assertThat(matches(routes, HttpMethod.DELETE, "/api/notifications/42")).isTrue();
        assertThat(matches(routes, HttpMethod.POST, "/api/firebase/register-token")).isTrue();
        assertThat(matches(routes, HttpMethod.GET, "/api/notifications/future-endpoint")).isFalse();
        assertThat(matches(routes, HttpMethod.PUT, "/api/notifications/future-endpoint/read")).isFalse();
        assertThat(matches(routes, HttpMethod.DELETE, "/api/notifications/future-endpoint")).isFalse();
        assertThat(matches(routes, HttpMethod.POST, "/api/firebase/future-endpoint")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/firebase/register-token")).isFalse();
    }

    @Test
    void onlyCustomerCatalogSearchIsAnonymous() {
        Map<String, Route> routes = routes();

        assertThat(matches(routes, HttpMethod.GET, "/api/search/restaurants?q=pho")).isTrue();
        assertThat(matches(routes, HttpMethod.GET, "/api/search/dishes?q=pho")).isTrue();
        assertThat(matches(routes, HttpMethod.GET, "/api/search/shippers?q=a")).isFalse();
    }

    @Test
    void nonMvpAnalyticsAndLivestreamSurfacesAreNotRouted() {
        Map<String, Route> routes = routes();

        assertThat(matches(routes, HttpMethod.GET, "/api/analytics/dashboard/admin")).isFalse();
        assertThat(matches(routes, HttpMethod.GET,
                "/api/analytics/dashboard/restaurant/42")).isFalse();
        assertThat(matches(routes, HttpMethod.POST,
                "/api/analytics/reconcile?date=2026-07-23")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/livestreams/active")).isFalse();
        assertThat(matches(routes, HttpMethod.POST, "/api/livestreams")).isFalse();
    }

    @Test
    void trackingFleetAndBusyApisAreNotRouted() {
        Map<String, Route> routes = routes();

        assertThat(matches(routes, HttpMethod.GET, "/api/tracking/shipper-locations/42")).isFalse();
        assertThat(matches(routes, HttpMethod.POST, "/api/tracking/shipper-locations/update")).isTrue();
        assertThat(matches(routes, HttpMethod.GET, "/api/tracking/shipper-locations/nearby")).isFalse();
        assertThat(matches(routes, HttpMethod.PUT, "/api/tracking/shipper-locations/42/busy")).isFalse();
        assertThat(matches(routes, HttpMethod.GET,
                "/api/deliveries/internal/10/tracking-access")).isFalse();
        assertThat(routes.get("tracking-service-ws").getFilters()).isNotEmpty();
        assertThat(matches(routes, HttpMethod.GET, "/ws/shipper-locations?deliveryId=42")).isTrue();
        assertThat(matches(routes, HttpMethod.GET, "/ws/shipper-locations/future-endpoint")).isFalse();
    }

    @Test
    void authUserLinkageApisAreNotPubliclyRouted() {
        Map<String, Route> routes = routes();

        assertThat(matches(routes, HttpMethod.POST, "/api/users")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/users/by-auth/42")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/users")).isTrue();
        assertThat(matches(routes, HttpMethod.PUT, "/api/users")).isTrue();
        assertThat(matches(routes, HttpMethod.PUT, "/api/users/42")).isFalse();
        assertThat(matches(routes, HttpMethod.DELETE, "/api/users/42")).isFalse();
        assertThat(routes.get("user-service-admin-read").getFilters()).isNotEmpty();
        assertThat(routes).doesNotContainKey("user-service-admin-status");
        assertThat(matches(routes, HttpMethod.POST, "/api/users/admin/42/block")).isFalse();
        assertThat(matches(routes, HttpMethod.POST, "/api/users/admin/42/unblock")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/users/admin/future-endpoint")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/addresses/future-endpoint")).isFalse();
        assertThat(matches(routes, HttpMethod.POST, "/api/addresses/42/default")).isFalse();
        assertThat(routes.get("user-address-service-read").getFilters()).isNotEmpty();
        assertThat(routes.get("user-address-service-create").getFilters()).isNotEmpty();
        assertThat(routes.get("user-address-service-update").getFilters()).isNotEmpty();
        assertThat(routes.get("user-address-service-default").getFilters()).isNotEmpty();
        assertThat(routes.get("user-address-service-delete").getFilters()).isNotEmpty();
    }

    @Test
    void restaurantCatalogIsPublicButInternalAndLegacySurfacesAreHidden() {
        Map<String, Route> routes = routes();

        assertThat(matches(routes, HttpMethod.GET, "/api/restaurants")).isTrue();
        assertThat(matches(routes, HttpMethod.GET, "/api/restaurants/42")).isTrue();
        assertThat(matches(routes, HttpMethod.GET, "/api/menu-items/restaurant/42/available")).isTrue();
        assertThat(routes.get("restaurant-catalog-public").getFilters()).isEmpty();
        assertThat(routes.get("restaurant-menu-public").getFilters()).isEmpty();

        assertThat(matches(routes, HttpMethod.POST, "/api/restaurants/validate/order")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/restaurants/creator/42")).isFalse();
        assertThat(matches(routes, HttpMethod.GET,
                "/api/restaurants/internal/42/owners/7")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/menu-items/creator/42")).isFalse();
        assertThat(routes.get("restaurant-admin-ratings-read").getFilters()).isNotEmpty();
        assertThat(routes.get("restaurant-admin-ratings-update").getFilters()).isNotEmpty();
        assertThat(routes.get("restaurant-order-actions").getFilters()).isNotEmpty();
        assertThat(routes.get("restaurant-owner-self").getFilters()).isNotEmpty();
        assertThat(routes.get("restaurant-customer-ratings-self").getFilters()).isNotEmpty();
        assertThat(routes.get("restaurant-menu-self").getFilters()).isNotEmpty();
        assertThat(routes).doesNotContainKey("restaurant-self");
        assertThat(matches(routes, HttpMethod.POST, "/api/restaurants/orders/42/future-action")).isFalse();
        assertThat(matches(routes, HttpMethod.DELETE, "/api/restaurants/admin/ratings/42")).isFalse();
    }

    @Test
    void genericOrderMutationsAndPathIdentityListsAreNotRouted() {
        Map<String, Route> routes = routes();

        assertThat(matches(routes, HttpMethod.POST, "/api/orders")).isTrue();
        assertThat(matches(routes, HttpMethod.POST, "/api/orders/checkout-preview")).isTrue();
        assertThat(routes.get("order-service-create").getFilters()).isNotEmpty();
        assertThat(matches(routes, HttpMethod.GET, "/api/orders/42")).isTrue();
        assertThat(matches(routes, HttpMethod.GET, "/api/orders/my-orders")).isTrue();
        assertThat(matches(routes, HttpMethod.GET, "/api/orders/my-restaurant-orders")).isTrue();
        assertThat(routes.get("order-service-customer-self").getFilters()).isNotEmpty();
        assertThat(routes.get("order-service-restaurant-self").getFilters()).isNotEmpty();
        assertThat(matches(routes, HttpMethod.PUT, "/api/orders/42/cancel")).isTrue();
        assertThat(routes.get("order-service-cancel").getFilters()).isNotEmpty();

        assertThat(matches(routes, HttpMethod.PUT, "/api/orders/42")).isFalse();
        assertThat(matches(routes, HttpMethod.DELETE, "/api/orders/42")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/orders/user/7")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/orders/restaurant/7")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/orders/shipper/7")).isFalse();
        assertThat(matches(routes, HttpMethod.GET,
                "/api/orders/internal/7/rating-eligibility")).isFalse();
        assertThat(matches(routes, HttpMethod.GET,
                "/api/orders/internal/7/restaurant-decision-eligibility?restaurantId=3")).isFalse();
        assertThat(matches(routes, HttpMethod.PUT, "/api/orders/42/status")).isFalse();
        assertThat(matches(routes, HttpMethod.PUT, "/api/orders/42/assign-shipper/8")).isFalse();
    }

    @Test
    void promotionCalculationAndReservationAreNotPubliclyRouted() {
        Map<String, Route> routes = routes();

        assertThat(matches(routes, HttpMethod.POST, "/api/promotions/collect/WELCOME")).isTrue();
        assertThat(matches(routes, HttpMethod.GET, "/api/promotions/my-vouchers")).isTrue();
        assertThat(matches(routes, HttpMethod.POST, "/api/promotions/calculate")).isFalse();
        assertThat(matches(routes, HttpMethod.POST, "/api/promotions/reserve")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/promotions/collect/WELCOME")).isFalse();
        assertThat(matches(routes, HttpMethod.POST, "/api/promotions/my-vouchers")).isFalse();

        assertThat(matches(routes, HttpMethod.GET, "/api/promotions/merchant")).isTrue();
        assertThat(matches(routes, HttpMethod.POST, "/api/promotions/merchant")).isFalse();
        assertThat(matches(routes, HttpMethod.POST, "/api/promotions/platform")).isTrue();
        assertThat(matches(routes, HttpMethod.GET, "/api/promotions/platform")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/promotions/admin")).isTrue();
        assertThat(matches(routes, HttpMethod.DELETE, "/api/promotions/42")).isTrue();
        assertThat(matches(routes, HttpMethod.GET, "/api/promotions/42")).isFalse();
    }

    @Test
    void legacyDeliveryAssignmentAndBroadMutationsAreNotRouted() {
        Map<String, Route> routes = routes();

        assertThat(matches(routes, HttpMethod.POST, "/api/deliveries/accept")).isTrue();
        assertThat(matches(routes, HttpMethod.POST, "/api/deliveries/cancel-assignment")).isTrue();
        assertThat(routes.get("delivery-service-shipper-actions").getFilters()).isNotEmpty();
        assertThat(matches(routes, HttpMethod.PUT, "/api/deliveries/42/status")).isTrue();
        assertThat(routes.get("delivery-service-status").getFilters()).isNotEmpty();
        assertThat(matches(routes, HttpMethod.GET, "/api/deliveries/offers/current")).isTrue();
        assertThat(routes.get("delivery-service-current-offer").getFilters()).isNotEmpty();
        assertThat(routes.get("delivery-service-current-offer").getPredicate().toString())
                .contains("/api/deliveries/offers/current")
                .doesNotContain("/shipper/{shipperId");
        assertThat(routes.get("delivery-service-shipper-read").getPredicate().toString())
                .doesNotContain("/offers/current");
        assertThat(matches(routes, HttpMethod.POST, "/api/deliveries/offers/current")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/deliveries/order/42")).isTrue();
        assertThat(matches(routes, HttpMethod.POST, "/api/deliveries/assign")).isFalse();
        assertThat(matches(routes, HttpMethod.POST, "/api/deliveries/admin/cancel-all-pending")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/deliveries/admin/future-endpoint")).isFalse();
        assertThat(matches(routes, HttpMethod.DELETE, "/api/deliveries/42")).isFalse();
        assertThat(matches(routes, HttpMethod.GET,
                "/api/deliveries/internal/42/tracking-access?userId=7&role=USER&shipperId=9")).isFalse();
    }

    @Test
    void shipperProfileRoutesAreScopedAndLegacyLocationIsHidden() {
        Map<String, Route> routes = routes();

        assertThat(matches(routes, HttpMethod.GET, "/api/shippers/my-profile")).isTrue();
        assertThat(matches(routes, HttpMethod.GET, "/api/shippers/42")).isTrue();
        assertThat(matches(routes, HttpMethod.DELETE, "/api/shippers")).isFalse();
        assertThat(matches(routes, HttpMethod.POST, "/api/shippers/42/ratings")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/shipper-locations/nearby")).isFalse();
        assertThat(routes.get("shipper-service-admin").getFilters()).isNotEmpty();
    }

    @Test
    void codFirstSettlementOnlyExposesAdminSurface() {
        Map<String, Route> routes = routes();

        assertThat(matches(routes, HttpMethod.GET, "/api/settlement/admin/balances")).isTrue();
        assertThat(routes.get("settlement-service-admin-read").getFilters()).isNotEmpty();
        assertThat(matches(routes, HttpMethod.POST, "/api/settlement/admin/transactions/42/reverse")).isFalse();
        assertThat(matches(routes, HttpMethod.POST, "/api/settlement/admin/transactions/42/approve")).isFalse();
        assertThat(matches(routes, HttpMethod.POST, "/api/settlement/balances/shipper/42/deposit")).isFalse();
        assertThat(matches(routes, HttpMethod.POST, "/api/settlement/balances/shipper/42/withdraw")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/settlement/payments/fake-confirm/PAY-1")).isFalse();
        assertThat(matches(routes, HttpMethod.POST, "/api/settlement/payments/create")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/settlement/transactions/42")).isFalse();
        assertThat(matches(routes, HttpMethod.GET,
                "/api/settlement/internal/shippers/42/cod-eligibility?codAmount=120000")).isFalse();
    }

    private Map<String, Route> routes() {
        Map<String, Route> routes = routeLocator.getRoutes().collectMap(Route::getId).block();
        assertThat(routes).isNotNull();
        return routes;
    }

    private boolean matches(Map<String, Route> routes, HttpMethod method, String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(method, path).build());
        return routes.values().stream()
                .anyMatch(route -> Boolean.TRUE.equals(
                        Mono.from(route.getPredicate().apply(exchange)).block()));
    }
}
