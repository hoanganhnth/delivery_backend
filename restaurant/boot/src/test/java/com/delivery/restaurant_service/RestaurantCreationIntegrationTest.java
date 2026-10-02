package com.delivery.restaurant_service;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.auth.resourceserver.security.AuthenticatedActorAuthenticationToken;
import com.delivery.identity.client.IdentityPrincipalClient;
import com.delivery.identity.contracts.IdentityLifecycleStatus;
import com.delivery.identity.contracts.IdentityPrincipal;
import com.delivery.identity.contracts.IdentityRole;
import com.delivery.restaurant.application.api.RestaurantCreationPort;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.repository.RestaurantOutboxEventRepository;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import com.delivery.restaurant_service.service.SearchSyncPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** H2 compatibility and transaction-boundary proof, not PostgreSQL concurrency proof. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:restaurant-creation;DB_CLOSE_DELAY=-1",
        "app.search-sync.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RestaurantCreationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RestaurantRepository restaurants;
    @Autowired RestaurantOutboxEventRepository outbox;
    @Autowired RestaurantCreationPort creationPort;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean IdentityPrincipalClient identity;
    @MockitoSpyBean SearchSyncPublisher search;

    @AfterEach
    void cleanTestDatabase() {
        outbox.deleteAll();
        restaurants.deleteAll();
    }

    @Test
    void shopOwnerCreationCommitsAllFieldsDefaultsAndSearchOutbox() throws Exception {
        assertThat(AopUtils.isAopProxy(creationPort)).isTrue();

        JsonNode data = create("SHOP_OWNER", null, 200);

        assertThat(data.size()).isEqualTo(17);
        assertThat(data.get("name").asText()).isEqualTo("Creation Restaurant");
        assertThat(data.get("address").asText()).isEqualTo("123 Main Street");
        assertThat(data.get("phone").asText()).isEqualTo("0123456789");
        assertThat(data.get("description").asText()).isEqualTo("Description");
        assertThat(data.get("image").asText()).isEqualTo("image.jpg");
        assertThat(data.get("latitude").asDouble()).isEqualTo(10.78);
        assertThat(data.get("longitude").asDouble()).isEqualTo(106.69);
        assertThat(data.get("openingHour").isNull()).isTrue();
        assertThat(data.get("closingHour").isNull()).isTrue();
        assertThat(data.get("defaultPrepTimeMinutes").asInt()).isEqualTo(30);
        assertThat(data.get("lifecycleStatus").asText()).isEqualTo("ACTIVE");
        assertThat(data.get("version").asLong()).isZero();
        assertThat(data.get("timeZone").asText()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(data.get("rating").asDouble()).isZero();
        assertThat(data.get("ratingCount").asInt()).isZero();
        assertThat(data.get("open").asBoolean()).isTrue();
        Restaurant saved = restaurants.findById(data.get("id").asLong()).orElseThrow();
        assertThat(saved.getOwnerPrincipalId()).isEqualTo(101L);
        assertThat(saved.getCreatorId()).isEqualTo(7L);
        var event = outbox.findAll().get(0);
        assertThat(event.getEventType()).isEqualTo("SEARCH_RESTAURANT_CREATE");
        assertThat(event.getTopic()).isEqualTo("entity-sync");
        JsonNode payload = json.readTree(event.getPayload());
        assertThat(payload.get("action").asText()).isEqualTo("CREATE");
        assertThat(payload.get("entityType").asText()).isEqualTo("RESTAURANT");
        assertThat(payload.get("entityId").asText()).isEqualTo(saved.getId().toString());
        assertThat(payload.get("payload").get("name").asText()).isEqualTo(saved.getName());
        assertThat(payload.get("payload").get("description").asText()).isEqualTo(saved.getDescription());
        assertThat(payload.get("payload").get("imageUrl").asText()).isEqualTo(saved.getImage());
        verifyNoInteractions(identity);
    }

    @Test
    void adminDirectoryLookupPrecedesTransactionAndRetainsCreatorIdentity() throws Exception {
        when(identity.findByPrincipalId(42L)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return Optional.of(new IdentityPrincipal(42L, IdentityRole.SHOP_OWNER, IdentityLifecycleStatus.ACTIVE));
        });
        JsonNode data = create("ADMIN", 42L, 200);
        Restaurant saved = restaurants.findById(data.get("id").asLong()).orElseThrow();
        assertThat(saved.getOwnerPrincipalId()).isEqualTo(42L);
        assertThat(saved.getCreatorId()).isEqualTo(7L);
        verify(identity).findByPrincipalId(42L);
    }

    @Test
    void rollbackDiscardsRestaurantOutbox() {
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            try {
                create("SHOP_OWNER", null, 200);
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
            assertThat(outbox.count()).isEqualTo(1);
            transaction.setRollbackOnly();
        });
        assertNoCreation();
    }

    @Test
    void failureAfterSearchOutboxWriteRollsBackBothRows() throws Exception {
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            invocation.callRealMethod();
            assertThat(outbox.count()).isEqualTo(1);
            throw new IllegalStateException("Injected failure after outbox write");
        }).when(AopTestUtils.<SearchSyncPublisher>getUltimateTargetObject(search))
                .publishRestaurantChange(any(), eq("CREATE"));

        create("SHOP_OWNER", null, 500);
        assertNoCreation();
    }

    @ParameterizedTest
    @CsvSource({"ADMIN,,400,OWNER_PRINCIPAL_REQUIRED", "SHOP_OWNER,42,403,CANNOT_ASSIGN_ANOTHER_OWNER",
            "CUSTOMER,,403,ACTOR_NOT_ALLOWED"})
    void invalidActorOrAssignmentPreservesHttpError(String role, Long owner, int status, String message) throws Exception {
        JsonNode response = request(role, owner, status);
        assertThat(response.get("message").asText()).isEqualTo(message);
        assertNoCreation();
        verifyNoInteractions(identity);
    }

    @Test
    void missingOwnerAndDirectoryFailurePreserveHttpErrorsWithoutWrites() throws Exception {
        when(identity.findByPrincipalId(42L)).thenReturn(Optional.empty());
        assertThat(request("ADMIN", 42L, 400).get("message").asText()).isEqualTo("INVALID_OWNER_PRINCIPAL");
        assertNoCreation();
        when(identity.findByPrincipalId(42L)).thenThrow(new IllegalStateException("auth unavailable"));
        assertThat(request("ADMIN", 42L, 500).get("message").asText()).isEqualTo("Đã xảy ra lỗi nội bộ.");
        assertNoCreation();
    }

    private void assertNoCreation() {
        assertThat(restaurants.count()).isZero();
        assertThat(outbox.count()).isZero();
    }

    private JsonNode create(String role, Long owner, int expectedStatus) throws Exception {
        return request(role, owner, expectedStatus).get("data");
    }

    private JsonNode request(String role, Long owner, int expectedStatus) throws Exception {
        var body = json.createObjectNode();
        body.put("name", "Creation Restaurant").put("address", "123 Main Street")
                .put("phone", "0123456789").put("description", "Description").put("image", "image.jpg")
                .put("addressLat", 10.78).put("addressLng", 106.69);
        // Explicit null must preserve the entity's prep-time default as well as omitted values.
        body.putNull("defaultPrepTimeMinutes");
        if (owner != null) body.put("ownerPrincipalId", owner);
        String response = mvc.perform(post("/api/restaurants").with(actor(role))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private RequestPostProcessor actor(String role) {
        var actor = new AuthenticatedActor(101L, 7L, "owner@example.com", Set.of(role));
        var jwt = Jwt.withTokenValue("test-token").header("alg", "RS256").claim("sub", "101")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
        return authentication(new AuthenticatedActorAuthenticationToken(jwt, actor, Collections.emptyList()));
    }
}
