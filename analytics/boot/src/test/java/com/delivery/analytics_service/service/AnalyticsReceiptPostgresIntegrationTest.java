package com.delivery.analytics_service.service;

import com.delivery.analytics_service.repository.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.function.IntConsumer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(EventProcessingService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
class AnalyticsReceiptPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("analytics_receipt").withUsername("analytics").withPassword("analytics");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired EventProcessingService service;
    @Autowired AnalyticsEventRepository events;
    @Autowired DailyOrderStatsRepository orders;
    @Autowired DailyRevenueStatsRepository revenue;
    @Autowired DailyItemSalesRepository items;

    @BeforeEach
    void cleanFixture() {
        items.deleteAll();
        orders.deleteAll();
        revenue.deleteAll();
        events.deleteAll();
    }

    @Test
    void concurrentExactReplayClaimsOneReceiptAndAggregatesOnce() throws Exception {
        race(8, ignored -> created(9L, payload("same", "")));
        assertThat(events.count()).isEqualTo(1);
        assertThat(orders.findAll()).hasSize(2).allSatisfy(row -> {
            assertThat(row.getTotalOrders()).isEqualTo(1);
            assertThat(row.getPendingOrders()).isEqualTo(1);
        });
        assertThat(items.count()).isZero();
    }

    @Test
    void distinctConcurrentReceiptsDoNotLosePlatformOrRestaurantIncrements() throws Exception {
        race(8, index -> created(100L + index, payload("distinct-" + index, "")));
        assertThat(events.count()).isEqualTo(8);
        assertThat(orders.findAll()).hasSize(2).allSatisfy(row -> {
            assertThat(row.getTotalOrders()).isEqualTo(8);
            assertThat(row.getPendingOrders()).isEqualTo(8);
        });
    }

    @Test
    void contradictoryReplayPreservesCommittedReceiptAndProjection() {
        created(9L, payload("contradiction", ""));
        assertThatThrownBy(() -> created(10L, payload("contradiction", "")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("contradictory");
        assertThat(events.count()).isEqualTo(1);
        assertThat(events.findAll().get(0).getOrderId()).isEqualTo(9);
        assertThat(orders.findAll()).hasSize(2).allSatisfy(row -> assertThat(row.getTotalOrders()).isEqualTo(1));
    }

    @Test
    void rejectedItemRollsBackReceiptAndCountersThenCorrectedRetryAggregatesOnce() {
        String invalid = payload("rollback", ",\"items\":[{\"menuItemId\":10,\"quantity\":1.5,\"price\":10}]");
        assertThatThrownBy(() -> created(9L, invalid)).isInstanceOf(IllegalArgumentException.class);
        assertThat(events.count()).isZero();
        assertThat(orders.count()).isZero();
        assertThat(items.count()).isZero();
        String valid = payload("rollback", ",\"items\":[{\"menuItemId\":10,\"quantity\":2,\"price\":10}]");
        created(9L, valid);
        created(9L, valid);
        assertThat(events.count()).isEqualTo(1);
        assertThat(orders.findAll()).hasSize(2).allSatisfy(row -> assertThat(row.getTotalOrders()).isEqualTo(1));
        var item = items.findByStatDateAndRestaurantIdAndMenuItemId(LocalDate.now(), 3L, 10L).orElseThrow();
        assertThat(item.getOrderedQuantity()).isEqualTo(2);
        assertThat(item.getOrderedRevenue()).isEqualByComparingTo("20");
    }

    @Test
    void v3UpgradePreservesRawReceiptAndEnforcesPositiveVersion() throws Exception {
        String schema = "analytics_upgrade";
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).target("3").load().migrate();
        try (var connection = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword()); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO " + schema + ".analytics_events "
                    + "(deduplication_key,event_type,event_time,raw_payload) "
                    + "VALUES ('legacy','ORDER_CREATED',CURRENT_TIMESTAMP,'{}')");
        }
        var flyway = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        try (var connection = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword()); var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("SELECT raw_payload,aggregate_version FROM " + schema
                    + ".analytics_events WHERE deduplication_key='legacy'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("{}");
                assertThat(result.getObject(2)).isNull();
            }
            assertThatThrownBy(() -> statement.executeUpdate("UPDATE " + schema
                    + ".analytics_events SET aggregate_version=0"))
                    .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("ck_analytics_aggregate_version_positive");
            assertThat(statement.executeUpdate("UPDATE " + schema + ".analytics_events SET aggregate_version=1"))
                    .isEqualTo(1);
        }
    }

    private void created(Long orderId, String payload) {
        service.processOrderCreated(orderId, 2L, 3L, "Kitchen", BigDecimal.TEN, "COD", payload);
    }

    private String payload(String eventId, String extra) {
        return "{\"eventId\":\"" + eventId + "\"" + extra + "}";
    }

    private void race(int count, IntConsumer operation) throws Exception {
        var ready = new CountDownLatch(count);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(count);
        try {
            var futures = new ArrayList<Future<?>>();
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    try {
                        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Race did not start");
                        operation.accept(index);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var future : futures) future.get(30, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
}
