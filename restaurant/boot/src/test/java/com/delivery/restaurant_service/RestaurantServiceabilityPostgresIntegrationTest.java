package com.delivery.restaurant_service;

import com.delivery.restaurant.application.DefaultRestaurantServiceabilityUseCase;
import com.delivery.restaurant.application.api.*;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import com.delivery.restaurant.domain.serviceability.*;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant.infrastructure.serviceability.JpaServiceabilityAdapter;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import com.delivery.restaurant_service.repository.RestaurantServiceabilityZoneRepository;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
        "app.outbox.relay-enabled=false", "app.search-sync.enabled=false", "order.service.url=http://order-service",
        "app.restaurant.serviceability-enabled=true", "app.identity.principal-ownership.enforced=true"
})
class RestaurantServiceabilityPostgresIntegrationTest {
    private static final String SQUARE = """
            {"type":"Polygon","coordinates":[[[106.6,10.7],[106.7,10.7],
            [106.7,10.8],[106.6,10.8],[106.6,10.7]]]}""";
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void fixture(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired RestaurantServiceabilityUseCase service;
    @Autowired RestaurantRepository restaurants;
    @Autowired RestaurantTransactionPort transactions;
    @Autowired JdbcTemplate sql;
    @Autowired RestaurantServiceabilityZoneRepository zones;
    @MockitoSpyBean JpaServiceabilityAdapter store;

    @Test void runtimeWiresCoreAndKeepsReadOnlyTransactions() {
        assertThat(service).isInstanceOf(DefaultRestaurantServiceabilityUseCase.class);
        assertThat(transactions.readOnly(() -> sql.queryForObject("show transaction_read_only", String.class))).isEqualTo("on");
        assertThat(transactions.required(() -> sql.queryForObject("show transaction_read_only", String.class))).isEqualTo("off");
    }

    @Test void realZonesPreservePriorityRevisionDefaultsPartialUpdatesAndDelete() {
        Long id = restaurant("serviceability-priority", 100L);
        var first = create(id, "First", null);
        var second = create(id, "Second", 5);
        assertThat(first.priority()).isZero(); assertThat(first.active()).isTrue();
        assertThat(first.revision()).isZero(); assertThat(first.createdAt()).isNotNull();
        assertThat(service.list(id, 100L, 200L, RestaurantActorRole.SHOP_OWNER)).extracting(ServiceabilityZoneResult::id)
                .containsExactly(second.id(), first.id());
        assertThat(service.evaluate(id, 10.75, 106.65).zoneId()).isEqualTo(second.id());
        assertThat(service.evaluate(id, 10.70, 106.65).serviceable()).isTrue();
        service.update(id, second.id(), new UpdateServiceabilityZoneCommand(second.revision(), " Renamed ", null, null, false),
                100L, 200L, RestaurantActorRole.SHOP_OWNER);
        var updated = zones.findById(second.id()).orElseThrow();
        assertThat(updated.getRevision()).isEqualTo(1L); assertThat(updated.getName()).isEqualTo("Renamed");
        assertThat(updated.getPriority()).isEqualTo(5); assertThat(updated.getPolygonGeoJson()).isEqualTo(SQUARE);
        assertThat(service.evaluate(id, 10.75, 106.65).zoneId()).isEqualTo(first.id());
        assertThatThrownBy(() -> service.update(id, second.id(),
                new UpdateServiceabilityZoneCommand(second.revision(), "Stale", null, null, null),
                100L, 200L, RestaurantActorRole.SHOP_OWNER)).isInstanceOf(ServiceabilityZoneConflictException.class);
        service.delete(id, first.id(), 100L, 200L, RestaurantActorRole.SHOP_OWNER);
        assertThat(zones.findById(first.id())).isEmpty();
    }

    @Test void ownershipAndMalformedConfigurationFailClosedOnRealStorage() {
        Long id = restaurant("serviceability-ownership", 100L);
        var zone = create(id, "Owned", 1);
        assertThatThrownBy(() -> service.list(id, 101L, 200L, RestaurantActorRole.SHOP_OWNER))
                .isInstanceOf(ServiceabilityAccessDeniedException.class);
        Long other = restaurant("serviceability-other", 100L);
        assertThatThrownBy(() -> service.delete(other, zone.id(), 100L, 200L, RestaurantActorRole.SHOP_OWNER))
                .isInstanceOf(ServiceabilityAccessDeniedException.class);
        sql.update("update restaurant_serviceability_zones set polygon_geo_json = ? where id = ?", "invalid", zone.id());
        assertThat(service.evaluate(id, 10.75, 106.65).reason()).isEqualTo("INVALID_ZONE_CONFIGURATION");
        assertThat(zones.findById(zone.id())).isPresent();
    }

    @Test void failureAfterJpaSaveRollsBackTheWholeCreation() {
        Long id = restaurant("serviceability-rollback", 100L);
        doAnswer(invocation -> { invocation.callRealMethod(); throw new IllegalStateException("fixture zone failure"); })
                .when(store).save(any(ServiceabilityZoneResult.class));
        try {
            assertThatThrownBy(() -> create(id, "Rollback", 1)).hasMessage("fixture zone failure");
            assertThat(zones.findByRestaurantIdOrderByPriorityDescIdAsc(id)).isEmpty();
        } finally { reset(store); }
    }

    @Test void simultaneousUpdatesAtTheSameRevisionHaveOneWinner() throws Exception {
        Long id = restaurant("serviceability-concurrency", 100L);
        var zone = create(id, "Original", 1);
        var saveBarrier = new CountDownLatch(2);
        doAnswer(invocation -> {
            saveBarrier.countDown();
            if (!saveBarrier.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("fixture save barrier timeout");
            return invocation.callRealMethod();
        }).when(store).save(any(ServiceabilityZoneResult.class));
        var wins = new AtomicInteger(); var conflicts = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Void> update = () -> {
                try {
                    service.update(id, zone.id(), new UpdateServiceabilityZoneCommand(zone.revision(),
                            Thread.currentThread().getName(), null, null, null), 100L, 200L, RestaurantActorRole.SHOP_OWNER);
                    wins.incrementAndGet();
                } catch (OptimisticLockingFailureException expected) { conflicts.incrementAndGet(); }
                return null;
            };
            var first = pool.submit(update); var second = pool.submit(update);
            first.get(20, TimeUnit.SECONDS); second.get(20, TimeUnit.SECONDS);
            assertThat(wins.get()).isEqualTo(1); assertThat(conflicts.get()).isEqualTo(1);
            assertThat(zones.findById(zone.id()).orElseThrow().getRevision()).isEqualTo(1L);
        } finally { pool.shutdownNow(); reset(store); }
    }

    private ServiceabilityZoneResult create(Long id, String name, Integer priority) {
        return service.create(id, new CreateServiceabilityZoneCommand(name, SQUARE, priority, null),
                100L, 200L, RestaurantActorRole.SHOP_OWNER);
    }
    private Long restaurant(String name, Long principalId) {
        Restaurant row = new Restaurant(); row.setName(name); row.setCreatorId(200L);
        row.setOwnerPrincipalId(principalId);
        return restaurants.saveAndFlush(row).getId();
    }
}
