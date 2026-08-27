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

import reactor.core.publisher.Mono;

@SpringBootTest(properties = "app.payment.client-api-enabled=true")
class PaymentClientGatewayRouteEnabledTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void paymentClientGateRoutesOnlyCreateAndReferenceLookup() {
        Map<String, Route> routes = routeLocator.getRoutes().collectMap(Route::getId).block();

        assertThat(routes).containsKeys(
                "settlement-service-customer-payment-create",
                "settlement-service-customer-payment-reference");
        assertThat(matches(routes, HttpMethod.POST, "/api/settlement/payments/create")).isTrue();
        assertThat(matches(routes, HttpMethod.GET, "/api/settlement/payments/ref/PAY-123")).isTrue();
        assertThat(matches(routes, HttpMethod.GET, "/api/settlement/payments/create")).isFalse();
        assertThat(matches(routes, HttpMethod.POST, "/api/settlement/payments/ref/PAY-123")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/settlement/payments/vnpay-callback")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/settlement/payments/vnpay-ipn")).isFalse();
        assertThat(matches(routes, HttpMethod.POST, "/api/settlement/payments/vnpay-ipn")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/settlement/payments/fake-confirm/PAY-123")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/settlement/payments/1")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/settlement/payments/providers")).isFalse();
    }

    private boolean matches(Map<String, Route> routes, HttpMethod method, String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(method, path).build());
        return routes.values().stream().anyMatch(route -> Boolean.TRUE.equals(
                Mono.from(route.getPredicate().apply(exchange)).block()));
    }
}
