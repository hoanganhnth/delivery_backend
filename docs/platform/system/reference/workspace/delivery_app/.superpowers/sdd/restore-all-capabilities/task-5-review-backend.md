# Review package: c2efe02..1add1e8

## Commits
1add1e8 feat(livestream): secure host lifecycle and gateway rollout

## Files changed
 .../src/main/resources/application.properties      |  1 +
 .../LivestreamClientGatewayRouteEnabledTest.java   | 50 ++++++++++++++++++++
 deploy/kubernetes/base/runtime-config.yaml         |  1 +
 .../client/RestaurantOwnershipClient.java          | 44 +++++++++++++++++
 .../controller/LivestreamController.java           | 16 +++++--
 .../controller/StreamTokenController.java          | 10 ++--
 .../service/LivestreamHostAuthorization.java       | 23 +++++++++
 .../src/main/resources/application.properties      |  2 +
 .../LivestreamControllerAuthorizationTest.java     | 55 ++++++++++++++++++++++
 .../service/LivestreamHostAuthorizationTest.java   | 27 +++++++++++
 10 files changed, 218 insertions(+), 11 deletions(-)

## Diff
diff --git a/api-gateway/src/main/resources/application.properties b/api-gateway/src/main/resources/application.properties
index aa63e9c..f28f756 100644
--- a/api-gateway/src/main/resources/application.properties
+++ b/api-gateway/src/main/resources/application.properties
@@ -69,10 +69,11 @@ spring.data.redis.port=${REDIS_PORT:6379}
 # Cấu hình định tuyến cho USER-SERVICE
 # spring.cloud.gateway.routes[1].id=user-service
 # spring.cloud.gateway.routes[1].uri=http://localhost:8082
 # spring.cloud.gateway.routes[1].predicates[0]=Path=/api/users/**
 # spring.cloud.gateway.routes[1].filters[0]=StripPrefix=2
 
 # Operational probes are isolated from the public Gateway listener. Compose
 # keeps this port private; do not add an API Gateway route or host port for it.
 # Customer payment traffic is a separate Gateway capability.
 app.payment.client-api-enabled=${PAYMENT_CLIENT_API_ENABLED:false}
+app.livestream.client-api-enabled=${LIVESTREAM_CLIENT_API_ENABLED:false}
diff --git a/api-gateway/src/test/java/com/delivery/api_gateway/config/LivestreamClientGatewayRouteEnabledTest.java b/api-gateway/src/test/java/com/delivery/api_gateway/config/LivestreamClientGatewayRouteEnabledTest.java
new file mode 100644
index 0000000..72ab099
--- /dev/null
+++ b/api-gateway/src/test/java/com/delivery/api_gateway/config/LivestreamClientGatewayRouteEnabledTest.java
@@ -0,0 +1,50 @@
+package com.delivery.api_gateway.config;
+
+import static org.assertj.core.api.Assertions.assertThat;
+
+import java.util.Map;
+import org.junit.jupiter.api.Test;
+import org.springframework.beans.factory.annotation.Autowired;
+import org.springframework.boot.test.context.SpringBootTest;
+import org.springframework.cloud.gateway.route.Route;
+import org.springframework.cloud.gateway.route.RouteLocator;
+import org.springframework.http.HttpMethod;
+import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
+import org.springframework.mock.web.server.MockServerWebExchange;
+import reactor.core.publisher.Mono;
+
+@SpringBootTest(properties = "app.livestream.client-api-enabled=true")
+class LivestreamClientGatewayRouteEnabledTest {
+
+    private static final String ID = "00000000-0000-4000-8000-000000000001";
+
+    @Autowired
+    private RouteLocator routeLocator;
+
+    @Test
+    void edgeGateExposesOnlyTheDocumentedViewerAndHostRoutes() {
+        Map<String, Route> routes = routeLocator.getRoutes().collectMap(Route::getId).block();
+
+        assertThat(routes).containsKeys("livestream-viewer", "livestream-host");
+        assertThat(matches(routes, HttpMethod.GET, "/api/livestreams/active")).isTrue();
+        assertThat(matches(routes, HttpMethod.GET, "/api/livestreams/" + ID)).isTrue();
+        assertThat(matches(routes, HttpMethod.POST, "/api/livestreams/" + ID + "/join")).isTrue();
+        assertThat(matches(routes, HttpMethod.POST, "/api/livestreams")).isTrue();
+        assertThat(matches(routes, HttpMethod.POST, "/api/livestreams/" + ID + "/start")).isTrue();
+        assertThat(matches(routes, HttpMethod.POST, "/api/livestreams/" + ID + "/end")).isTrue();
+        assertThat(matches(routes, HttpMethod.GET, "/api/livestreams/restaurant/42")).isTrue();
+
+        assertThat(matches(routes, HttpMethod.POST, "/api/livestreams/" + ID + "/token")).isFalse();
+        assertThat(matches(routes, HttpMethod.GET, "/api/livestreams/seller/42")).isFalse();
+        assertThat(matches(routes, HttpMethod.GET, "/api/livestreams/not-a-uuid")).isFalse();
+        assertThat(matches(routes, HttpMethod.GET, "/api/livestreams/" + ID + "/products")).isFalse();
+        assertThat(matches(routes, HttpMethod.PUT, "/api/livestreams/" + ID + "/end")).isFalse();
+    }
+
+    private boolean matches(Map<String, Route> routes, HttpMethod method, String path) {
+        MockServerWebExchange exchange = MockServerWebExchange.from(
+                MockServerHttpRequest.method(method, path).build());
+        return routes.values().stream().anyMatch(route -> Boolean.TRUE.equals(
+                Mono.from(route.getPredicate().apply(exchange)).block()));
+    }
+}
diff --git a/deploy/kubernetes/base/runtime-config.yaml b/deploy/kubernetes/base/runtime-config.yaml
index d58a6e9..11100c2 100644
--- a/deploy/kubernetes/base/runtime-config.yaml
+++ b/deploy/kubernetes/base/runtime-config.yaml
@@ -73,20 +73,21 @@ data:
   SETTLEMENT_DELIVERY_EXCEPTION_PROCESSING_ENABLED: "false"
   REFUND_OUTBOX_RELAY_ENABLED: "false"
   PROMOTION_CHECKOUT_ENABLED: "false"
   PROMOTION_OUTBOX_RELAY_ENABLED: "false"
   PROMOTION_MERCHANT_CREATE_API_ENABLED: "false"
   FLASHSALE_CHECKOUT_ENABLED: "false"
   FLASHSALE_OUTBOX_RELAY_ENABLED: "false"
   FLASHSALE_MERCHANT_REGISTRATION_ENABLED: "false"
   ANALYTICS_PROCESSING_ENABLED: "false"
   LIVESTREAM_API_ENABLED: "false"
+  LIVESTREAM_CLIENT_API_ENABLED: "false"
   # Production Matching v1 remains dark until PostgreSQL/Redis invariants,
   # staging smoke and client capability rollout gates are explicitly passed.
   ORDER_VOUCHER_STACKING_ENABLED: "false"
   ORDER_VOUCHER_STACKING_CANARY_PRINCIPALS: ""
   PROMOTION_STACKING_ENABLED: "false"
   PROMOTION_STACKING_CANARY_PRINCIPALS: ""
   MATCHING_H3_ENABLED: "false"
   MATCHING_BATCH_ENABLED: "false"
   MATCHING_BATCH_SCHEDULER_ENABLED: "false"
   MATCHING_BATCH_CLIENT_CAPABILITY_ENABLED: "false"
diff --git a/livestream-service/src/main/java/com/delivery/livestream_service/client/RestaurantOwnershipClient.java b/livestream-service/src/main/java/com/delivery/livestream_service/client/RestaurantOwnershipClient.java
new file mode 100644
index 0000000..a85f121
--- /dev/null
+++ b/livestream-service/src/main/java/com/delivery/livestream_service/client/RestaurantOwnershipClient.java
@@ -0,0 +1,44 @@
+package com.delivery.livestream_service.client;
+
+import com.delivery.livestream_service.exception.UnauthorizedLivestreamAccessException;
+import java.util.Map;
+import org.springframework.beans.factory.annotation.Value;
+import org.springframework.stereotype.Component;
+import org.springframework.web.client.RestClient;
+
+/** Server-only bridge to the canonical restaurant ownership boundary. */
+@Component
+public class RestaurantOwnershipClient {
+    private final RestClient client;
+    private final String internalSecret;
+
+    public RestaurantOwnershipClient(
+            @Value("${restaurant.service.url:http://restaurant-service:8083}") String restaurantUrl,
+            @Value("${app.internal.secret:}") String internalSecret) {
+        this.client = RestClient.builder().baseUrl(restaurantUrl).build();
+        this.internalSecret = internalSecret;
+    }
+
+    public void requireOwnedBy(Long restaurantId, Long principalId, Long legacyUserId) {
+        if (restaurantId == null || principalId == null || legacyUserId == null
+                || internalSecret == null || internalSecret.isBlank()) {
+            throw new UnauthorizedLivestreamAccessException("Restaurant ownership cannot be verified");
+        }
+        try {
+            Map<String, Object> envelope = client.get()
+                    .uri("/api/restaurants/internal/{restaurantId}/owners/{principalId}?legacyOwnerId={legacyUserId}",
+                            restaurantId, principalId, legacyUserId)
+                    .header("Internal-Token", internalSecret)
+                    .retrieve()
+                    .body(Map.class);
+            if (envelope == null || !Integer.valueOf(1).equals(envelope.get("status"))
+                    || !Boolean.TRUE.equals(envelope.get("data"))) {
+                throw new UnauthorizedLivestreamAccessException("You do not own this restaurant");
+            }
+        } catch (UnauthorizedLivestreamAccessException denied) {
+            throw denied;
+        } catch (RuntimeException unavailable) {
+            throw new UnauthorizedLivestreamAccessException("Restaurant ownership cannot be verified");
+        }
+    }
+}
diff --git a/livestream-service/src/main/java/com/delivery/livestream_service/controller/LivestreamController.java b/livestream-service/src/main/java/com/delivery/livestream_service/controller/LivestreamController.java
index 735becc..90e0fd2 100644
--- a/livestream-service/src/main/java/com/delivery/livestream_service/controller/LivestreamController.java
+++ b/livestream-service/src/main/java/com/delivery/livestream_service/controller/LivestreamController.java
@@ -1,76 +1,82 @@
 package com.delivery.livestream_service.controller;
 
 import com.delivery.livestream_service.common.constants.ApiPathConstants;
 import com.delivery.livestream_service.dto.request.CreateLivestreamRequest;
 import com.delivery.livestream_service.dto.response.JoinLivestreamResponse;
 import com.delivery.livestream_service.dto.response.LivestreamResponse;
 import com.delivery.livestream_service.dto.response.StartLivestreamResponse;
+import com.delivery.livestream_service.exception.UnauthorizedLivestreamAccessException;
 import com.delivery.livestream_service.payload.BaseResponse;
 import com.delivery.livestream_service.service.LivestreamService;
+import com.delivery.livestream_service.service.LivestreamHostAuthorization;
 import com.delivery.auth.resourceserver.security.AuthenticatedActor;
 
 import jakarta.validation.Valid;
 import org.springframework.http.ResponseEntity;
 import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
-import org.springframework.security.access.AccessDeniedException;
 import org.springframework.security.core.annotation.AuthenticationPrincipal;
 import org.springframework.web.bind.annotation.*;
 
 import java.util.List;
 import java.util.UUID;
 
 @RestController
 @RequestMapping(ApiPathConstants.LIVESTREAMS)
 @ConditionalOnProperty(name = "app.livestream.api-enabled", havingValue = "true")
 public class LivestreamController {
 
     private final LivestreamService livestreamService;
+    private final LivestreamHostAuthorization hostAuthorization;
 
-    public LivestreamController(LivestreamService livestreamService) {
+    public LivestreamController(LivestreamService livestreamService, LivestreamHostAuthorization hostAuthorization) {
         this.livestreamService = livestreamService;
+        this.hostAuthorization = hostAuthorization;
     }
 
     @PostMapping
     public ResponseEntity<BaseResponse<LivestreamResponse>> createLivestream(
             @Valid @RequestBody CreateLivestreamRequest request,
             @AuthenticationPrincipal AuthenticatedActor actor) {
         requireActor(actor);
+        hostAuthorization.requireHost(actor, request.getRestaurantId());
         LivestreamResponse response = livestreamService.createLivestream(request, actor.getUserId(), getRoleString(actor));
         return ResponseEntity.ok(new BaseResponse<>(1, response, "Tạo livestream thành công"));
     }
 
     @PostMapping("/{id}/start")
     public ResponseEntity<BaseResponse<StartLivestreamResponse>> startLivestream(
             @PathVariable UUID id,
             @AuthenticationPrincipal AuthenticatedActor actor) {
         requireActor(actor);
+        hostAuthorization.requireHost(actor, livestreamService.getLivestreamById(id).getRestaurantId());
         StartLivestreamResponse response = livestreamService.startLivestream(id, actor.getUserId(), getRoleString(actor));
         return ResponseEntity.ok(new BaseResponse<>(1, response, 
                 "Bắt đầu livestream thành công. Sử dụng token và channelName để join Agora."));
     }
 
     @PostMapping("/{id}/join")
     public ResponseEntity<BaseResponse<JoinLivestreamResponse>> joinLivestream(
             @PathVariable UUID id,
             @AuthenticationPrincipal AuthenticatedActor actor) {
         requireActor(actor);
         JoinLivestreamResponse response = livestreamService.joinLivestream(id, actor.getUserId());
         return ResponseEntity.ok(new BaseResponse<>(1, response, 
                 "Join livestream thành công. Sử dụng token và channelName để xem trên Agora."));
     }
 
     @PostMapping("/{id}/end")
     public ResponseEntity<BaseResponse<LivestreamResponse>> endLivestream(
             @PathVariable UUID id,
             @AuthenticationPrincipal AuthenticatedActor actor) {
         requireActor(actor);
+        hostAuthorization.requireHost(actor, livestreamService.getLivestreamById(id).getRestaurantId());
         LivestreamResponse response = livestreamService.endLivestream(id, actor.getUserId(), getRoleString(actor));
         return ResponseEntity.ok(new BaseResponse<>(1, response, "Kết thúc livestream thành công"));
     }
 
     @GetMapping("/active")
     public ResponseEntity<BaseResponse<List<LivestreamResponse>>> getActiveLivestreams() {
         List<LivestreamResponse> response = livestreamService.getActiveLivestreams();
         return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy danh sách livestream đang live thành công"));
     }
 
@@ -82,28 +88,30 @@ public class LivestreamController {
 
     @GetMapping("/seller/{sellerId}")
     public ResponseEntity<BaseResponse<List<LivestreamResponse>>> getLivestreamsBySeller(
             @PathVariable Long sellerId) {
         List<LivestreamResponse> response = livestreamService.getLivestreamsBySeller(sellerId);
         return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy danh sách livestream của seller thành công"));
     }
 
     @GetMapping("/restaurant/{restaurantId}")
     public ResponseEntity<BaseResponse<List<LivestreamResponse>>> getLivestreamsByRestaurant(
-            @PathVariable Long restaurantId) {
+            @PathVariable Long restaurantId, @AuthenticationPrincipal AuthenticatedActor actor) {
+        requireActor(actor);
+        hostAuthorization.requireHost(actor, restaurantId);
         List<LivestreamResponse> response = livestreamService.getLivestreamsByRestaurant(restaurantId);
         return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy danh sách livestream của restaurant thành công"));
     }
 
     private void requireActor(AuthenticatedActor actor) {
         if (actor == null || actor.getUserId() == null) {
-            throw new AccessDeniedException("Yêu cầu đăng nhập");
+            throw new UnauthorizedLivestreamAccessException("Yêu cầu đăng nhập");
         }
     }
 
     private String getRoleString(AuthenticatedActor actor) {
         if (actor == null) return null;
         if (actor.isAdmin()) return "ADMIN";
         if (actor.isShopOwner()) return "SHOP_OWNER";
         return "USER";
     }
 }
diff --git a/livestream-service/src/main/java/com/delivery/livestream_service/controller/StreamTokenController.java b/livestream-service/src/main/java/com/delivery/livestream_service/controller/StreamTokenController.java
index 0116fed..916cce4 100644
--- a/livestream-service/src/main/java/com/delivery/livestream_service/controller/StreamTokenController.java
+++ b/livestream-service/src/main/java/com/delivery/livestream_service/controller/StreamTokenController.java
@@ -1,23 +1,23 @@
 package com.delivery.livestream_service.controller;
 
 import com.delivery.livestream_service.common.constants.ApiPathConstants;
 import com.delivery.livestream_service.dto.request.GenerateTokenRequest;
 import com.delivery.livestream_service.dto.response.TokenResponse;
+import com.delivery.livestream_service.exception.UnauthorizedLivestreamAccessException;
 import com.delivery.livestream_service.payload.BaseResponse;
 import com.delivery.livestream_service.service.StreamTokenService;
 import com.delivery.auth.resourceserver.security.AuthenticatedActor;
 
 import jakarta.validation.Valid;
 import org.springframework.http.ResponseEntity;
 import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
-import org.springframework.security.access.AccessDeniedException;
 import org.springframework.security.core.annotation.AuthenticationPrincipal;
 import org.springframework.web.bind.annotation.*;
 
 import java.util.UUID;
 
 @RestController
 @ConditionalOnProperty(name = "app.livestream.api-enabled", havingValue = "true")
 @RequestMapping(ApiPathConstants.LIVESTREAMS)
 public class StreamTokenController {
 
@@ -25,18 +25,14 @@ public class StreamTokenController {
 
     public StreamTokenController(StreamTokenService streamTokenService) {
         this.streamTokenService = streamTokenService;
     }
 
     @PostMapping("/{id}/token")
     public ResponseEntity<BaseResponse<TokenResponse>> generateToken(
             @PathVariable UUID id,
             @Valid @RequestBody GenerateTokenRequest request,
             @AuthenticationPrincipal AuthenticatedActor actor) {
-        if (actor == null || actor.getUserId() == null) {
-            throw new AccessDeniedException("Yêu cầu đăng nhập");
-        }
-        TokenResponse response = streamTokenService.generateToken(
-                id, actor.getUserId(), request.getRole(), request.getExpireSeconds());
-        return ResponseEntity.ok(new BaseResponse<>(1, response, "Tạo token thành công"));
+        throw new UnauthorizedLivestreamAccessException(
+                "Caller-controlled livestream token issuance is disabled");
     }
 }
diff --git a/livestream-service/src/main/java/com/delivery/livestream_service/service/LivestreamHostAuthorization.java b/livestream-service/src/main/java/com/delivery/livestream_service/service/LivestreamHostAuthorization.java
new file mode 100644
index 0000000..135c80c
--- /dev/null
+++ b/livestream-service/src/main/java/com/delivery/livestream_service/service/LivestreamHostAuthorization.java
@@ -0,0 +1,23 @@
+package com.delivery.livestream_service.service;
+
+import com.delivery.auth.resourceserver.security.AuthenticatedActor;
+import com.delivery.livestream_service.client.RestaurantOwnershipClient;
+import com.delivery.livestream_service.exception.UnauthorizedLivestreamAccessException;
+import org.springframework.stereotype.Service;
+
+@Service
+public class LivestreamHostAuthorization {
+    private final RestaurantOwnershipClient restaurants;
+
+    public LivestreamHostAuthorization(RestaurantOwnershipClient restaurants) {
+        this.restaurants = restaurants;
+    }
+
+    public void requireHost(AuthenticatedActor actor, Long restaurantId) {
+        if (actor == null || actor.getPrincipalId() == null || actor.getLegacyUserId() == null
+                || !actor.isShopOwner()) {
+            throw new UnauthorizedLivestreamAccessException("SHOP_OWNER role is required");
+        }
+        restaurants.requireOwnedBy(restaurantId, actor.getPrincipalId(), actor.getLegacyUserId());
+    }
+}
diff --git a/livestream-service/src/main/resources/application.properties b/livestream-service/src/main/resources/application.properties
index b7ff0e4..8d36c7e 100644
--- a/livestream-service/src/main/resources/application.properties
+++ b/livestream-service/src/main/resources/application.properties
@@ -16,20 +16,22 @@ management.health.readinessstate.enabled=true
 management.endpoint.health.group.readiness.include=*
 management.endpoint.health.group.liveness.include=livenessState
 management.endpoint.health.show-details=never
 management.endpoint.health.show-components=never
 management.prometheus.metrics.export.enabled=${METRICS_PROMETHEUS_ENABLED:true}
 management.metrics.tags.application=${spring.application.name}
 management.metrics.distribution.percentiles.http.server.requests=0.5,0.95,0.99
 management.metrics.distribution.percentiles-histogram.http.server.requests=true
 server.port=8094
 app.livestream.api-enabled=${LIVESTREAM_API_ENABLED:false}
+restaurant.service.url=${RESTAURANT_SERVICE_URL:http://restaurant-service:8083}
+app.internal.secret=${INTERNAL_SECRET:}
 
 # DataSource config (PostgreSQL)
 spring.datasource.url=jdbc:postgresql://localhost:5432/livestream_db
 spring.datasource.username=postgres
 spring.datasource.password=${DB_PASSWORD:}
 spring.datasource.driver-class-name=org.postgresql.Driver
 
 # Hibernate/JPA config
 spring.jpa.hibernate.ddl-auto=validate
 spring.jpa.show-sql=${JPA_SHOW_SQL:false}
diff --git a/livestream-service/src/test/java/com/delivery/livestream_service/controller/LivestreamControllerAuthorizationTest.java b/livestream-service/src/test/java/com/delivery/livestream_service/controller/LivestreamControllerAuthorizationTest.java
new file mode 100644
index 0000000..c0c72c2
--- /dev/null
+++ b/livestream-service/src/test/java/com/delivery/livestream_service/controller/LivestreamControllerAuthorizationTest.java
@@ -0,0 +1,55 @@
+package com.delivery.livestream_service.controller;
+
+import static org.assertj.core.api.Assertions.assertThatThrownBy;
+import static org.mockito.Mockito.mock;
+import static org.mockito.Mockito.verify;
+import static org.mockito.Mockito.verifyNoInteractions;
+import static org.mockito.Mockito.when;
+
+import com.delivery.auth.resourceserver.security.AuthenticatedActor;
+import com.delivery.livestream_service.dto.response.LivestreamResponse;
+import com.delivery.livestream_service.exception.UnauthorizedLivestreamAccessException;
+import com.delivery.livestream_service.service.LivestreamHostAuthorization;
+import com.delivery.livestream_service.service.LivestreamService;
+import java.util.Set;
+import java.util.UUID;
+import org.junit.jupiter.api.Test;
+
+class LivestreamControllerAuthorizationTest {
+
+    private final LivestreamService livestreams = mock(LivestreamService.class);
+    private final LivestreamHostAuthorization hostAuthorization = mock(LivestreamHostAuthorization.class);
+    private final LivestreamController controller = new LivestreamController(livestreams, hostAuthorization);
+
+    @Test
+    void viewerJoinDoesNotRequireRestaurantHostOwnership() {
+        UUID livestreamId = UUID.randomUUID();
+        AuthenticatedActor viewer = new AuthenticatedActor(10L, 10L, "viewer@example.test", Set.of("USER"));
+
+        controller.joinLivestream(livestreamId, viewer);
+
+        verify(livestreams).joinLivestream(livestreamId, 10L);
+        verifyNoInteractions(hostAuthorization);
+    }
+
+    @Test
+    void endRequiresHostOwnershipForTheStreamRestaurant() {
+        UUID livestreamId = UUID.randomUUID();
+        AuthenticatedActor owner = new AuthenticatedActor(11L, 11L, "owner@example.test", Set.of("SHOP_OWNER"));
+        LivestreamResponse stream = new LivestreamResponse();
+        stream.setRestaurantId(42L);
+        when(livestreams.getLivestreamById(livestreamId)).thenReturn(stream);
+
+        controller.endLivestream(livestreamId, owner);
+
+        verify(hostAuthorization).requireHost(owner, 42L);
+    }
+
+    @Test
+    void callerControlledTokenEndpointFailsClosedWithTheAuthorizationException() {
+        StreamTokenController tokens = new StreamTokenController(mock());
+
+        assertThatThrownBy(() -> tokens.generateToken(UUID.randomUUID(), null, null))
+                .isInstanceOf(UnauthorizedLivestreamAccessException.class);
+    }
+}
diff --git a/livestream-service/src/test/java/com/delivery/livestream_service/service/LivestreamHostAuthorizationTest.java b/livestream-service/src/test/java/com/delivery/livestream_service/service/LivestreamHostAuthorizationTest.java
new file mode 100644
index 0000000..2a5a855
--- /dev/null
+++ b/livestream-service/src/test/java/com/delivery/livestream_service/service/LivestreamHostAuthorizationTest.java
@@ -0,0 +1,27 @@
+package com.delivery.livestream_service.service;
+
+import static org.assertj.core.api.Assertions.assertThatThrownBy;
+import static org.mockito.Mockito.mock;
+import static org.mockito.Mockito.verifyNoInteractions;
+
+import com.delivery.auth.resourceserver.security.AuthenticatedActor;
+import com.delivery.livestream_service.client.RestaurantOwnershipClient;
+import com.delivery.livestream_service.exception.UnauthorizedLivestreamAccessException;
+import java.util.Set;
+import org.junit.jupiter.api.Test;
+
+class LivestreamHostAuthorizationTest {
+
+    private final RestaurantOwnershipClient restaurants = mock(RestaurantOwnershipClient.class);
+    private final LivestreamHostAuthorization authorization = new LivestreamHostAuthorization(restaurants);
+
+    @Test
+    void nonShopOwnerIsDeniedWithoutAnOwnershipLookup() {
+        AuthenticatedActor customer = new AuthenticatedActor(11L, 11L, "customer@example.test", Set.of("USER"));
+
+        assertThatThrownBy(() -> authorization.requireHost(customer, 42L))
+                .isInstanceOf(UnauthorizedLivestreamAccessException.class);
+
+        verifyNoInteractions(restaurants);
+    }
+}
