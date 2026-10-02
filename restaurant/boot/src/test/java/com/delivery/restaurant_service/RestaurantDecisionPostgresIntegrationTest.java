package com.delivery.restaurant_service;

import com.delivery.restaurant.application.api.*;
import com.delivery.restaurant.domain.decision.RestaurantDecisionConflictException;
import com.delivery.restaurant_service.entity.RestaurantOutboxEvent;
import com.delivery.restaurant_service.repository.RestaurantOrderDecisionRepository;
import com.delivery.restaurant_service.repository.RestaurantOutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.flyway.enabled=true", "spring.kafka.listener.auto-startup=false",
        "app.outbox.relay-enabled=false", "app.search-sync.enabled=false", "order.service.url=http://order-service"
})
class RestaurantDecisionPostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void fixture(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired RestaurantOrderDecisionUseCase decisions;
    @Autowired RestaurantOrderDecisionRepository rows;
    @Autowired ObjectMapper mapper;
    @MockitoSpyBean RestaurantOutboxEventRepository outbox;
    @MockitoBean OrderDecisionEligibilityPort orders;

    @Test void retainedFingerprintRejectsContradictionAfterOutboxPruning() throws Exception {
        decisions.confirm(99101L, 7L, 70L, 20, "Fixture");
        var event = outbox.findAll().stream().filter(row -> row.getAggregateId().equals("99101")).findFirst().orElseThrow();
        var payload = mapper.readTree(event.getPayload());
        assertThat(payload.path("orderId").asLong()).isEqualTo(99101L);
        assertThat(payload.path("actorUserId").asLong()).isEqualTo(70L);
        assertThat(payload.path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(payload.path("action").asText()).isEqualTo("CONFIRM");
        assertThat(payload.path("decisionFingerprint").asText()).isEqualTo(rows.findById(99101L).orElseThrow().getPayloadFingerprint());
        outbox.delete(event);
        decisions.confirm(99101L, 7L, 70L, 20, "Fixture");
        assertThatThrownBy(() -> decisions.confirm(99101L, 7L, 70L, 21, "Fixture"))
                .isInstanceOf(RestaurantDecisionConflictException.class).hasMessageContaining("contradictory payload");
        assertThat(outbox.findAll().stream().filter(row -> row.getAggregateId().equals("99101"))).isEmpty();
    }

    @Test void simultaneousOppositeDecisionsProduceOneAuthoritativeEvent() throws Exception {
        var start = new CountDownLatch(1); var wins = new AtomicInteger(); var conflicts = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Void> confirm = () -> decide(start, wins, conflicts, () -> decisions.confirm(99102L, 7L, 70L, 20, null));
            Callable<Void> reject = () -> decide(start, wins, conflicts, () -> decisions.reject(99102L, 7L, 70L, "Closed"));
            var first = pool.submit(confirm); var second = pool.submit(reject); start.countDown();
            first.get(20, TimeUnit.SECONDS); second.get(20, TimeUnit.SECONDS);
            assertThat(wins.get()).isEqualTo(1); assertThat(conflicts.get()).isEqualTo(1);
            assertThat(rows.findById(99102L)).isPresent();
            assertThat(outbox.findAll().stream().filter(row -> row.getAggregateId().equals("99102"))).hasSize(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void outboxFailureRollsBackDecisionAndAllowsLaterRetry() {
        doThrow(new IllegalStateException("fixture outbox failure")).when(outbox).save(any(RestaurantOutboxEvent.class));
        try {
            assertThatThrownBy(() -> decisions.reject(99103L, 7L, 70L, "Closed")).hasMessage("fixture outbox failure");
            assertThat(rows.findById(99103L)).isEmpty();
        } finally { reset(outbox); }
        decisions.reject(99103L, 7L, 70L, "Closed");
        assertThat(rows.findById(99103L)).isPresent();
        assertThat(outbox.findAll().stream().filter(row -> row.getAggregateId().equals("99103"))).hasSize(1);
    }

    private Void decide(CountDownLatch start, AtomicInteger wins, AtomicInteger conflicts, Runnable action) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        try { action.run(); wins.incrementAndGet(); }
        catch (RestaurantDecisionConflictException expected) { conflicts.incrementAndGet(); }
        return null;
    }
}
