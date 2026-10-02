package com.delivery.restaurant_service;

import com.delivery.restaurant.application.DefaultMenuItemInventoryUseCase;
import com.delivery.restaurant.application.api.*;
import com.delivery.restaurant.domain.inventory.*;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import com.delivery.restaurant.infrastructure.inventory.JpaInventoryAdapter;
import com.delivery.restaurant.infrastructure.inventory.JsonInventoryOrderEventAdapter;
import com.delivery.restaurant_service.entity.*;
import com.delivery.restaurant_service.repository.*;
import java.math.BigDecimal;
import java.util.*;
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
        "app.outbox.relay-enabled=false", "app.search-sync.enabled=false", "order.service.url=http://order-service",
        "app.restaurant.inventory-enabled=true", "app.restaurant.inventory-consumer-enabled=false"
})
class RestaurantInventoryPostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void fixture(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired MenuItemInventoryUseCase inventory;
    @Autowired JsonInventoryOrderEventAdapter events;
    @Autowired MenuItemInventoryOrderReceiptRepository receipts;
    @Autowired RestaurantRepository restaurants;
    @Autowired MenuItemRepository items;
    @Autowired MenuItemInventoryRepository stocks;
    @Autowired MenuItemInventoryReservationRepository reservations;
    @Autowired RestaurantTransactionPort transactions;
    @Autowired JdbcTemplate sql;
    @MockitoSpyBean JpaInventoryAdapter store;

    @Test void coreReservesCompleteCartAndRejectsContradictoryReplay() {
        assertThat(inventory).isInstanceOf(DefaultMenuItemInventoryUseCase.class);
        var fixture = fixture(5, 0); UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> inventory.reserve(command(id, 99201L, fixture,
                List.of(line(fixture.first(), 2), line(fixture.second(), 1)))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Insufficient inventory");
        assertThat(stocks.findById(fixture.first()).orElseThrow().getReservedQuantity()).isZero();
        assertThat(reservations.findById(id)).isEmpty();
        var command = command(id, 99201L, fixture, List.of(line(fixture.first(), 2)));
        var first = inventory.reserve(command);
        assertThat(inventory.reserve(command)).isEqualTo(first);
        assertThat(stocks.findById(fixture.first()).orElseThrow().getReservedQuantity()).isEqualTo(2);
        assertThatThrownBy(() -> inventory.reserve(command(id, 99201L, fixture, List.of(line(fixture.first(), 3)))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("replay payload");
    }

    @Test void concurrentOrdersCannotOversellLastUnit() throws Exception {
        var fixture = fixture(1, 0); var start = new CountDownLatch(1);
        var wins = new AtomicInteger(); var unavailable = new AtomicInteger(); var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Void> first = () -> reserveAfter(start, wins, unavailable, fixture, 99202L);
            Callable<Void> second = () -> reserveAfter(start, wins, unavailable, fixture, 99203L);
            var a = pool.submit(first); var b = pool.submit(second); start.countDown();
            a.get(20, TimeUnit.SECONDS); b.get(20, TimeUnit.SECONDS);
            assertThat(wins.get()).isEqualTo(1); assertThat(unavailable.get()).isEqualTo(1);
            assertThat(stocks.findById(fixture.first()).orElseThrow().getReservedQuantity()).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void commitAndCancellationCompensateExactlyOnceAndExpiryOnlyReleasesHold() {
        var fixture = fixture(5, 0); UUID sold = UUID.randomUUID();
        inventory.reserve(command(sold, 99204L, fixture, List.of(line(fixture.first(), 2))));
        assertThat(inventory.commit(sold, 99204L).state()).isEqualTo("COMMITTED");
        assertThat(inventory.commit(sold, 99204L).state()).isEqualTo("COMMITTED");
        assertThat(inventory.getInventory(fixture.first()).onHandQuantity()).isEqualTo(3);
        assertThat(inventory.release(sold, 99204L).state()).isEqualTo("RELEASED");
        inventory.release(sold, 99204L);
        assertThat(inventory.getInventory(fixture.first()).onHandQuantity()).isEqualTo(5);
        assertThat(inventory.getInventory(fixture.first()).reservedQuantity()).isZero();
        UUID expired = UUID.randomUUID();
        inventory.reserve(command(expired, 99205L, fixture, List.of(line(fixture.first(), 1))));
        sql.update("update menu_item_inventory_reservations set expires_at = current_timestamp - interval '1 minute' where reservation_id = ?", expired);
        assertThat(inventory.commit(expired, 99205L).state()).isEqualTo("EXPIRED");
        assertThat(inventory.getInventory(fixture.first()).onHandQuantity()).isEqualTo(5);
        assertThat(inventory.getInventory(fixture.first()).reservedQuantity()).isZero();
        UUID sweep = UUID.randomUUID();
        inventory.reserve(command(sweep, 99206L, fixture, List.of(line(fixture.first(), 1))));
        sql.update("update menu_item_inventory_reservations set expires_at = current_timestamp - interval '1 minute' where reservation_id = ?", sweep);
        assertThat(inventory.expireReservations()).isEqualTo(1);
        assertThat(inventory.expireReservations()).isZero();
    }

    @Test void failedReservationInsertRollsBackAllStockChanges() {
        var fixture = fixture(5, 5); UUID id = UUID.randomUUID();
        doAnswer(invocation -> { invocation.callRealMethod(); throw new IllegalStateException("fixture insert failure"); })
                .when(store).insertReservation(any(InventoryReservation.class));
        try {
            assertThatThrownBy(() -> inventory.reserve(command(id, 99207L, fixture,
                    List.of(line(fixture.first(), 2), line(fixture.second(), 1)))))
                    .hasMessage("fixture insert failure");
            assertThat(reservations.findById(id)).isEmpty();
            assertThat(inventory.getInventory(fixture.first()).reservedQuantity()).isZero();
            assertThat(inventory.getInventory(fixture.second()).reservedQuantity()).isZero();
            assertThat(inventory.getInventory(fixture.first()).revision()).isZero();
        } finally { reset(store); }
    }

    @Test void inventoryEditsPreserveOwnerAuthorizationAndRevision() {
        var fixture = fixture(5, 0);
        assertThatThrownBy(() -> inventory.updateInventory(fixture.first(), edit(8, 0L, 201L)))
                .isInstanceOf(InventoryAccessDeniedException.class);
        var updated = inventory.updateInventory(fixture.first(), edit(8, 0L, 200L));
        assertThat(updated.onHandQuantity()).isEqualTo(8); assertThat(updated.revision()).isEqualTo(1L);
        assertThatThrownBy(() -> inventory.updateInventory(fixture.first(), edit(9, 0L, 200L)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("revision is stale");
        assertThat(inventory.getInventory(fixture.first()).onHandQuantity()).isEqualTo(8);
    }

    @Test void concurrentCommitReplayReadsStateAfterAcquiringReservationLock() throws Exception {
        var fixture = fixture(5, 0); UUID id = UUID.randomUUID();
        inventory.reserve(command(id, 99208L, fixture, List.of(line(fixture.first(), 2))));
        var pool = Executors.newFixedThreadPool(2); List<Future<InventoryReservationResult>> work = new ArrayList<>();
        try {
            // Hold stock so the first commit cannot finish before the second waits for the reservation.
            transactions.required(() -> {
                store.lockStocks(List.of(fixture.first()));
                work.add(pool.submit(() -> inventory.commit(id, 99208L)));
                work.add(pool.submit(() -> inventory.commit(id, 99208L)));
                awaitTwoDatabaseLockWaiters();
                return null;
            });
            for (var future : work) assertThat(future.get(20, TimeUnit.SECONDS).state()).isEqualTo("COMMITTED");
            assertThat(inventory.getInventory(fixture.first()).onHandQuantity()).isEqualTo(3);
            assertThat(inventory.getInventory(fixture.first()).reservedQuantity()).isZero();
            assertThat(inventory.getInventory(fixture.first()).revision()).isEqualTo(2L);
        } finally { pool.shutdownNow(); }
    }

    @Test void expiryRechecksCancellationCommittedAfterCandidateSelection() {
        var fixture = fixture(5, 0); UUID id = UUID.randomUUID();
        inventory.reserve(command(id, 99209L, fixture, List.of(line(fixture.first(), 2))));
        sql.update("update menu_item_inventory_reservations set expires_at = current_timestamp - interval '1 minute' where reservation_id = ?", id);
        var pool = Executors.newSingleThreadExecutor();
        doAnswer(invocation -> {
            var candidates = invocation.callRealMethod();
            assertThat(pool.submit(() -> inventory.release(id, 99209L)).get(10, TimeUnit.SECONDS).state()).isEqualTo("RELEASED");
            return candidates;
        }).when(store).dueReservationIds(any(java.time.LocalDateTime.class));
        try {
            assertThat(inventory.expireReservations()).isZero();
            assertThat(reservations.findById(id).orElseThrow().getState()).isEqualTo(MenuItemInventoryReservation.State.RELEASED);
            assertThat(inventory.getInventory(fixture.first()).reservedQuantity()).isZero();
        } finally { pool.shutdownNow(); reset(store); }
    }

    @Test void rawOrderEventRetryAndRefundHaveExactlyOnceLedgerEffects() throws Exception {
        var fixture = fixture(5, 0); UUID reservation = UUID.randomUUID(), createdEvent = UUID.randomUUID();
        inventory.reserve(command(reservation, 99210L, fixture, List.of(line(fixture.first(), 2))));
        String created = eventPayload(createdEvent, 99210L, reservation);
        events.process(created, "order.created");
        events.process(created, "order.created-retry-inventory-1");
        assertThat(inventory.getInventory(fixture.first()).onHandQuantity()).isEqualTo(3);
        assertThat(receipts.findById(createdEvent).orElseThrow().getSourceTopic()).isEqualTo("order.created");
        assertThatThrownBy(() -> events.process(created + " ", "order.created"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("contradictory");
        UUID refundEvent = UUID.randomUUID();
        events.process(eventPayload(refundEvent, 99210L, reservation), "order.refund-eligible");
        events.process(eventPayload(UUID.randomUUID(), 99210L, reservation), "order.cancelled");
        assertThat(receipts.findById(refundEvent).orElseThrow().getAction()).isEqualTo("RELEASE");
        assertThat(inventory.getInventory(fixture.first()).onHandQuantity()).isEqualTo(5);
        assertThat(inventory.getInventory(fixture.first()).revision()).isEqualTo(3L);
    }

    @Test void receiptAndInventoryTransitionRollbackTogetherOnFailure() throws Exception {
        var fixture = fixture(5, 0); UUID reservation = UUID.randomUUID(), eventId = UUID.randomUUID();
        inventory.reserve(command(reservation, 99211L, fixture, List.of(line(fixture.first(), 2))));
        String payload = eventPayload(eventId, 99211L, reservation);
        doAnswer(invocation -> { invocation.callRealMethod(); throw new IllegalStateException("fixture state failure"); })
                .when(store).changeReservationState(any(UUID.class), any(InventoryReservation.State.class));
        try {
            assertThatThrownBy(() -> events.process(payload, "order.created")).hasMessage("fixture state failure");
            assertThat(receipts.findById(eventId)).isEmpty();
            assertThat(inventory.getInventory(fixture.first()).onHandQuantity()).isEqualTo(5);
            assertThat(inventory.getInventory(fixture.first()).reservedQuantity()).isEqualTo(2);
            assertThat(reservations.findById(reservation).orElseThrow().getState()).isEqualTo(MenuItemInventoryReservation.State.RESERVED);
        } finally { reset(store); }
        events.process(payload, "order.created");
        assertThat(receipts.findById(eventId)).isPresent();
        assertThat(inventory.getInventory(fixture.first()).onHandQuantity()).isEqualTo(3);
    }

    @Test void concurrentDuplicateEventCommitsOneReceiptAndOneTransition() throws Exception {
        var fixture = fixture(5, 0); UUID reservation = UUID.randomUUID(), eventId = UUID.randomUUID();
        inventory.reserve(command(reservation, 99212L, fixture, List.of(line(fixture.first(), 2))));
        String payload = eventPayload(eventId, 99212L, reservation); var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Void> consume = () -> { start.await(10, TimeUnit.SECONDS); events.process(payload, "order.created"); return null; };
            var first = pool.submit(consume); var second = pool.submit(consume); start.countDown();
            first.get(20, TimeUnit.SECONDS); second.get(20, TimeUnit.SECONDS);
            assertThat(sql.queryForObject("select count(*) from menu_item_inventory_order_receipts where event_id = ?", Integer.class, eventId)).isEqualTo(1);
            assertThat(inventory.getInventory(fixture.first()).onHandQuantity()).isEqualTo(3);
            assertThat(inventory.getInventory(fixture.first()).revision()).isEqualTo(2L);
        } finally { pool.shutdownNow(); }
    }

    @Test void orderEventWithoutReservationStillRecordsExactReplayIdentity() throws Exception {
        var fixture = fixture(5, 0); UUID eventId = UUID.randomUUID();
        String payload = "{\"eventId\":\"" + eventId + "\",\"orderId\":99213}";
        events.process(payload, "order.created"); events.process(payload, "order.created");
        assertThat(receipts.findById(eventId).orElseThrow().getReservationId()).isNull();
        assertThat(inventory.getInventory(fixture.first()).onHandQuantity()).isEqualTo(5);
    }

    private static String eventPayload(UUID eventId, Long orderId, UUID reservation) {
        return "{\"eventId\":\"" + eventId + "\",\"orderId\":" + orderId + ",\"inventoryReservationId\":\"" + reservation + "\"}";
    }

    private void awaitTwoDatabaseLockWaiters() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer count = sql.queryForObject("select count(*) from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock' and pid <> pg_backend_pid()", Integer.class);
            if (count != null && count >= 2) return;
            try { Thread.sleep(20); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
        }
        throw new IllegalStateException("fixture did not observe two database lock waiters");
    }
    private Void reserveAfter(CountDownLatch start, AtomicInteger wins, AtomicInteger unavailable, Fixture fixture, Long order) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        try { inventory.reserve(command(UUID.randomUUID(), order, fixture, List.of(line(fixture.first(), 1)))); wins.incrementAndGet(); }
        catch (IllegalArgumentException expected) { assertThat(expected).hasMessageContaining("Insufficient inventory"); unavailable.incrementAndGet(); }
        return null;
    }
    private static UpdateMenuItemInventoryCommand edit(int quantity, Long revision, Long owner) { return new UpdateMenuItemInventoryCommand(quantity, revision, owner, RestaurantActorRole.SHOP_OWNER); }
    private static InventoryReservationLineCommand line(Long item, int quantity) { return new InventoryReservationLineCommand(item, quantity); }
    private static InventoryReservationCommand command(UUID id, Long order, Fixture f, List<InventoryReservationLineCommand> lines) { return new InventoryReservationCommand(id, order, 300L, 400L, f.restaurant(), lines); }
    private Fixture fixture(int firstQuantity, int secondQuantity) {
        Restaurant restaurant = new Restaurant(); restaurant.setName("inventory-" + UUID.randomUUID()); restaurant.setCreatorId(200L);
        restaurant = restaurants.saveAndFlush(restaurant);
        return new Fixture(restaurant.getId(), item(restaurant, firstQuantity), item(restaurant, secondQuantity));
    }
    private Long item(Restaurant restaurant, int quantity) {
        MenuItem item = new MenuItem(); item.setRestaurant(restaurant); item.setName("Fixture"); item.setPrice(BigDecimal.TEN); item.setStatus(MenuItem.Status.AVAILABLE);
        Long id = items.saveAndFlush(item).getId();
        MenuItemInventory stock = new MenuItemInventory(); stock.setMenuItemId(id); stock.setOnHandQuantity(quantity); stocks.saveAndFlush(stock);
        return id;
    }
    private record Fixture(Long restaurant, Long first, Long second) { }
}
