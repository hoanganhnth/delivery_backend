package com.delivery.api_gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

@SpringBootTest(properties = "app.livestream.client-api-enabled=true")
class LivestreamClientGatewayRouteEnabledTest {

    private static final String ID = "00000000-0000-4000-8000-000000000001";

    @Autowired
    private RouteLocator routeLocator;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void enabledLivestreamRoutesDoNotInstallTheDisabledResponseFilter() {
        assertThat(applicationContext.getBeansOfType(LivestreamDisabledWebFilter.class)).isEmpty();
    }

    @Test
    void edgeGateExposesOnlyTheDocumentedViewerAndHostRoutes() {
        Map<String, Route> routes = routeLocator.getRoutes().collectMap(Route::getId).block();

        assertThat(routes).containsKeys(
                "livestream-viewer",
                "livestream-host",
                "livestream-products-read",
                "livestream-products-host");
        assertThat(matches(routes, HttpMethod.GET, "/api/livestreams/active")).isTrue();
        assertThat(matches(routes, HttpMethod.GET, "/api/livestreams/" + ID)).isTrue();
        assertThat(matches(routes, HttpMethod.POST, "/api/livestreams/" + ID + "/join")).isTrue();
        assertThat(matches(routes, HttpMethod.POST, "/api/livestreams/" + ID + "/token/renew")).isTrue();
        assertThat(matches(routes, HttpMethod.POST, "/api/livestreams")).isTrue();
        assertThat(matches(routes, HttpMethod.POST, "/api/livestreams/" + ID + "/start")).isTrue();
        assertThat(matches(routes, HttpMethod.POST, "/api/livestreams/" + ID + "/end")).isTrue();
        assertThat(matches(routes, HttpMethod.GET, "/api/livestreams/restaurant/42")).isTrue();
        assertThat(matches(routes, HttpMethod.GET, "/api/livestreams/" + ID + "/products")).isTrue();
        assertThat(matches(routes, HttpMethod.GET, "/api/livestreams/" + ID + "/products/pinned")).isTrue();
        assertThat(matches(routes, HttpMethod.POST, "/api/livestreams/" + ID + "/products/pin")).isTrue();
        assertThat(matches(routes, HttpMethod.DELETE,
                "/api/livestreams/" + ID + "/products/42/pin")).isTrue();
        assertThat(matches(routes, HttpMethod.DELETE,
                "/api/livestreams/" + ID + "/products/42")).isTrue();

        assertThat(matches(routes, HttpMethod.POST, "/api/livestreams/" + ID + "/token")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/livestreams/" + ID + "/token/renew")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/livestreams")).isFalse();
        assertThat(matches(routes, HttpMethod.POST, "/api/livestreams/active")).isFalse();
        assertThat(matches(routes, HttpMethod.POST, "/api/livestreams/restaurant/42")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/livestreams/" + ID + "/join")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/livestreams/seller/42")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/livestreams/not-a-uuid")).isFalse();
        assertThat(matches(routes, HttpMethod.PUT, "/api/livestreams/" + ID + "/products")).isFalse();
        assertThat(matches(routes, HttpMethod.GET, "/api/livestreams/not-a-uuid/products")).isFalse();
        assertThat(matches(routes, HttpMethod.DELETE,
                "/api/livestreams/" + ID + "/products/not-a-product")).isFalse();
        assertThat(matches(routes, HttpMethod.PUT, "/api/livestreams/" + ID + "/end")).isFalse();
    }

    private boolean matches(Map<String, Route> routes, HttpMethod method, String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(method, path).build());
        return routes.values().stream().anyMatch(route -> Boolean.TRUE.equals(
                Mono.from(route.getPredicate().apply(exchange)).block()));
    }

    @Test
    void moderationAndProductWritesUseExactMethods() {
        Map<String, Route> routes = routeLocator.getRoutes().collectMap(Route::getId).block();
        String room = "/api/livestreams/" + ID;
        assertThat(matches(routes, HttpMethod.GET, "/api/livestreams/admin")).isTrue();
        assertThat(matches(routes, HttpMethod.POST, "/api/livestreams/admin")).isFalse();
        assertThat(matches(routes, HttpMethod.POST, room + "/moderation")).isTrue();
        assertThat(matches(routes, HttpMethod.GET, room + "/moderation")).isFalse();
        assertThat(matches(routes, HttpMethod.DELETE, room + "/moderation")).isFalse();
        assertThat(matches(routes, HttpMethod.DELETE, room + "/products/pin")).isFalse();
        assertThat(matches(routes, HttpMethod.POST, room + "/products/42/pin")).isFalse();
        assertThat(matches(routes, HttpMethod.POST, room + "/products/42")).isFalse();
    }
}
