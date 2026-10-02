package com.delivery.restaurant_service;

import com.delivery.restaurant.application.DefaultCatalogLifecycleUseCase;
import com.delivery.restaurant.application.api.CatalogLifecycleUseCase;
import com.delivery.restaurant.domain.catalog.*;
import com.delivery.restaurant_service.entity.*;
import com.delivery.restaurant_service.repository.*;
import com.delivery.restaurant_service.service.JpaCatalogLifecycleAdapter;
import com.delivery.restaurant_service.service.RestaurantService;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
        "app.outbox.relay-enabled=false", "app.search-sync.enabled=true", "order.service.url=http://order-service"
})
class RestaurantLifecyclePostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void fixture(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired CatalogLifecycleUseCase lifecycle;
    @Autowired RestaurantService httpService;
    @Autowired RestaurantRepository restaurants;
    @Autowired MenuItemRepository menus;
    @Autowired JdbcTemplate sql;
    @MockitoSpyBean CatalogLifecycleAuditRepository audits;
    @MockitoSpyBean RestaurantOutboxEventRepository outbox;
    @MockitoSpyBean JpaCatalogLifecycleAdapter adapter;

    @Test void transitionAuditAndSearchOutboxCommitTogetherAndReplayHasNoSideEffects() {
        var fixture = fixture();
        assertThat(lifecycle).isInstanceOf(DefaultCatalogLifecycleUseCase.class);
        var changed = lifecycle.changeRestaurant(fixture.restaurant(), RestaurantStatus.PAUSED, 0L, 100L, 200L, "SHOP_OWNER");
        assertThat(changed.version()).isEqualTo(1L);
        assertThat(menus.findById(fixture.menu()).orElseThrow().getStatus()).isEqualTo(MenuItem.Status.AVAILABLE);
        assertThat(auditCount("RESTAURANT", fixture.restaurant())).isEqualTo(1);
        var audit = audits.findAll().stream().filter(row -> row.getAggregateType().equals("RESTAURANT") && row.getAggregateId().equals(fixture.restaurant())).findFirst().orElseThrow();
        assertThat(audit.getBeforeStatus()).isEqualTo("ACTIVE"); assertThat(audit.getAfterStatus()).isEqualTo("PAUSED");
        assertThat(audit.getBeforeVersion()).isZero(); assertThat(audit.getAfterVersion()).isEqualTo(1L);
        assertThat(audit.getActorPrincipalId()).isEqualTo(100L); assertThat(audit.getCorrelationId()).isNotBlank();
        assertThat(outboxCount("RESTAURANT", fixture.restaurant())).isEqualTo(1);
        lifecycle.changeRestaurant(fixture.restaurant(), RestaurantStatus.PAUSED, 1L, 100L, 200L, "SHOP_OWNER");
        assertThat(auditCount("RESTAURANT", fixture.restaurant())).isEqualTo(1);
        assertThat(outboxCount("RESTAURANT", fixture.restaurant())).isEqualTo(1);
    }

    @Test void archiveThroughHttpFacadeUsesCoreTransactionAndPreservesMenuState() {
        var fixture = fixture();
        httpService.deleteRestaurant(fixture.restaurant(), 100L, 200L, "SHOP_OWNER");
        assertThat(restaurants.findById(fixture.restaurant()).orElseThrow().getLifecycleStatus()).isEqualTo(RestaurantStatus.ARCHIVED);
        assertThat(menus.findById(fixture.menu()).orElseThrow().getStatus()).isEqualTo(MenuItem.Status.AVAILABLE);
        assertThat(outbox.findAll().stream().filter(row -> row.getAggregateId().equals("RESTAURANT:" + fixture.restaurant())).findFirst().orElseThrow().getEventType()).isEqualTo("SEARCH_RESTAURANT_DELETE");
        assertThat(auditCount("RESTAURANT", fixture.restaurant())).isEqualTo(1);
    }

    @Test void onlyAdminRestoresArchivedAggregatesAndBothTransitionsProduceAudits() {
        var fixture = fixture();
        lifecycle.changeRestaurant(fixture.restaurant(), RestaurantStatus.ARCHIVED, 0L, 100L, 200L, "SHOP_OWNER");
        assertThatThrownBy(() -> lifecycle.changeRestaurant(fixture.restaurant(), RestaurantStatus.PAUSED, 1L, 100L, 200L, "SHOP_OWNER")).isInstanceOf(IllegalArgumentException.class);
        lifecycle.changeRestaurant(fixture.restaurant(), RestaurantStatus.PAUSED, 1L, 999L, 999L, "ADMIN");
        assertThat(restaurants.findById(fixture.restaurant()).orElseThrow().getVersion()).isEqualTo(2L);
        lifecycle.changeMenuItem(fixture.menu(), MenuItemStatus.ARCHIVED, 0L, 100L, 200L, "SHOP_OWNER");
        assertThatThrownBy(() -> lifecycle.changeMenuItem(fixture.menu(), MenuItemStatus.DISCONTINUED, 1L, 100L, 200L, "SHOP_OWNER")).isInstanceOf(IllegalArgumentException.class);
        lifecycle.changeMenuItem(fixture.menu(), MenuItemStatus.DISCONTINUED, 1L, 999L, 999L, "ADMIN");
        assertThat(menus.findById(fixture.menu()).orElseThrow().getStatus()).isEqualTo(MenuItem.Status.DISCONTINUED);
        assertThat(auditCount("MENU_ITEM", fixture.menu())).isEqualTo(2);
        assertThat(outboxCount("DISH", fixture.menu())).isEqualTo(2);
    }

    @Test void auditFailureRollsBackStateAndVersionAndPreventsOutbox() {
        var fixture = fixture();
        doThrow(new IllegalStateException("fixture audit failure")).when(audits).save(any(CatalogLifecycleAudit.class));
        try {
            assertThatThrownBy(() -> lifecycle.changeRestaurant(fixture.restaurant(), RestaurantStatus.PAUSED, 0L, 100L, 200L, "SHOP_OWNER")).hasMessage("fixture audit failure");
            assertThat(restaurants.findById(fixture.restaurant()).orElseThrow().getLifecycleStatus()).isEqualTo(RestaurantStatus.ACTIVE);
            assertThat(restaurants.findById(fixture.restaurant()).orElseThrow().getVersion()).isZero();
            assertThat(auditCount("RESTAURANT", fixture.restaurant())).isZero(); assertThat(outboxCount("RESTAURANT", fixture.restaurant())).isZero();
        } finally { reset(audits); }
    }

    @Test void outboxFailureRollsBackMenuStateAndAuditThenAllowsRetry() {
        var fixture = fixture();
        doThrow(new IllegalStateException("fixture outbox failure")).when(outbox).save(any(RestaurantOutboxEvent.class));
        try {
            assertThatThrownBy(() -> lifecycle.changeMenuItem(fixture.menu(), MenuItemStatus.SOLD_OUT, 0L, 100L, 200L, "SHOP_OWNER")).isInstanceOf(IllegalStateException.class).hasRootCauseMessage("fixture outbox failure");
            assertThat(menus.findById(fixture.menu()).orElseThrow().getStatus()).isEqualTo(MenuItem.Status.AVAILABLE);
            assertThat(menus.findById(fixture.menu()).orElseThrow().getVersion()).isZero();
            assertThat(auditCount("MENU_ITEM", fixture.menu())).isZero();
        } finally { reset(outbox); }
        lifecycle.changeMenuItem(fixture.menu(), MenuItemStatus.SOLD_OUT, 0L, 100L, 200L, "SHOP_OWNER");
        assertThat(auditCount("MENU_ITEM", fixture.menu())).isEqualTo(1); assertThat(outboxCount("DISH", fixture.menu())).isEqualTo(1);
    }

    @Test void simultaneousCommandsAtOneVersionCommitOneAuditAndOneEvent() throws Exception {
        var fixture = fixture(); var ready = new CountDownLatch(2); var wins = new AtomicInteger(); var stale = new AtomicInteger();
        doAnswer(invocation -> { ready.countDown(); if (!ready.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("fixture barrier timeout"); return invocation.callRealMethod(); })
                .when(adapter).saveRestaurantStatus(any(Long.class), any(RestaurantStatus.class));
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Void> change = () -> {
                try { lifecycle.changeRestaurant(fixture.restaurant(), RestaurantStatus.PAUSED, 0L, 100L, 200L, "SHOP_OWNER"); wins.incrementAndGet(); }
                catch (StaleVersionException expected) { stale.incrementAndGet(); }
                return null;
            };
            var first = pool.submit(change); var second = pool.submit(change); first.get(20, TimeUnit.SECONDS); second.get(20, TimeUnit.SECONDS);
            assertThat(wins.get()).isEqualTo(1); assertThat(stale.get()).isEqualTo(1);
            assertThat(auditCount("RESTAURANT", fixture.restaurant())).isEqualTo(1); assertThat(outboxCount("RESTAURANT", fixture.restaurant())).isEqualTo(1);
        } finally { pool.shutdownNow(); reset(adapter); }
    }
    private int auditCount(String type, Long id) { return sql.queryForObject("select count(*) from catalog_lifecycle_audits where aggregate_type = ? and aggregate_id = ?", Integer.class, type, id); }
    private int outboxCount(String type, Long id) { return sql.queryForObject("select count(*) from restaurant_outbox_events where aggregate_id = ?", Integer.class, type+":"+id); }
    private Fixture fixture() {
        Restaurant row = new Restaurant(); row.setName("lifecycle-"+UUID.randomUUID()); row.setCreatorId(200L); row.setOwnerPrincipalId(100L);
        row = restaurants.saveAndFlush(row);
        MenuItem menu = new MenuItem(); menu.setName("Meal"); menu.setPrice(BigDecimal.TEN); menu.setRestaurant(row); menu.setStatus(MenuItem.Status.AVAILABLE);
        return new Fixture(row.getId(), menus.saveAndFlush(menu).getId());
    }
    private record Fixture(Long restaurant, Long menu) { }
}
