package com.delivery.restaurant_service;

import com.delivery.restaurant.application.api.*;
import com.delivery.restaurant.domain.catalog.RestaurantStatus;
import com.delivery.restaurant.domain.inventory.InventoryReservation;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import com.delivery.restaurant.infrastructure.inventory.JpaInventoryAdapter;
import com.delivery.restaurant_service.entity.*;
import com.delivery.restaurant_service.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Testcontainers(disabledWithoutDocker = true)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = RestaurantServiceApplication.class, properties = {
        "spring.config.location=file:src/main/resources/application.properties",
        "spring.config.import=", "spring.cloud.config.enabled=false", "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false", "management.server.port=0", "server.port=0", "app.internal.secret=restaurant-runtime-fixture",
        "spring.kafka.admin.auto-create=true", "spring.kafka.admin.fail-fast=true", "spring.kafka.listener.auto-startup=true",
        "app.restaurant.inventory-enabled=true", "app.restaurant.inventory-consumer-enabled=true",
        "app.restaurant.serviceability-enabled=true", "app.identity.principal-ownership.enforced=true",
        "app.kafka.retry.auto-create-topics=true", "app.kafka.retry.initial-delay-ms=50", "app.kafka.retry.max-delay-ms=50",
        "app.kafka.retry.multiplier=2", "app.outbox.relay-enabled=true", "app.outbox.poll-delay-ms=100",
        "app.search-sync.enabled=true"
})
@Import(RestaurantKafkaPostgresIntegrationTest.Topics.class)
class RestaurantKafkaPostgresIntegrationTest {
    static final String CREATED = "order.created.restaurant-runtime-proof";
    static final String CANCELLED = "order.cancelled.restaurant-runtime-proof";
    static final String REFUND = "order.refund-eligible.restaurant-runtime-proof";
    static final String GROUP = "restaurant-inventory-runtime-proof";
    static final String SQUARE = "{\"type\":\"Polygon\",\"coordinates\":[[[106.6,10.7],[106.7,10.7],[106.7,10.8],[106.6,10.8],[106.6,10.7]]]}";
    @Container static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void containers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("app.kafka.topics.order-created", () -> CREATED);
        registry.add("app.kafka.topics.order-cancelled", () -> CANCELLED);
        registry.add("app.kafka.topics.refund-eligible", () -> REFUND);
        registry.add("app.restaurant.inventory-consumer-group", () -> GROUP);
    }
    @Autowired MenuItemInventoryUseCase inventory;
    @Autowired OrderValidationUseCase validation;
    @Autowired RestaurantServiceabilityUseCase serviceability;
    @Autowired CatalogLifecycleUseCase lifecycle;
    @Autowired RestaurantRepository restaurants;
    @Autowired MenuItemRepository menus;
    @Autowired MenuItemInventoryRepository stocks;
    @Autowired MenuItemInventoryReservationRepository reservations;
    @Autowired MenuItemInventoryOrderReceiptRepository receipts;
    @Autowired RestaurantOutboxEventRepository outbox;
    @Autowired ObjectMapper mapper;
    @Autowired @Qualifier("inventoryRetryKafkaTemplate") KafkaTemplate<String, String> kafka;
    @MockitoSpyBean JpaInventoryAdapter store;

    @Test void productionKafkaAndPostgresPreserveInventoryAtomicityReplayDltOffsetsAndSearchPublication() throws Exception {
        var f = fixture();
        var quote = validation.validate(new OrderValidationCommand(f.restaurant(), 10.75, 106.65,
                List.of(new OrderValidationLineCommand(f.menu(), 2))));
        assertThat(quote.isValid()).isTrue(); assertThat(quote.calculatedTotal()).isEqualTo(85.0);
        assertThat(quote.restaurantInfo().serviceable()).isTrue(); assertThat(quote.itemValidations().get(0).availableStock()).isEqualTo(5);
        assertThat(validation.validate(new OrderValidationCommand(f.restaurant(), 11.0, 106.65,
                List.of(new OrderValidationLineCommand(f.menu(), 6)))).errors())
                .extracting(OrderValidationError::errorCode).containsExactly("OUTSIDE_ACTIVE_ZONES", "INSUFFICIENT_STOCK");

        UUID held = UUID.randomUUID(); long order = 99301L;
        inventory.reserve(command(f, held, order)); UUID event = UUID.randomUUID(); String created = raw(event, order, held);
        send(CREATED, created); await(() -> receipts.existsById(event));
        assertThat(reservations.findById(held).orElseThrow().getState()).isEqualTo(MenuItemInventoryReservation.State.COMMITTED);
        assertStock(f, 3, 0); send(CREATED, created);
        try (var dlt = consumer(CREATED + ".inventory.DLT", "restaurant-conflict-proof")) {
            String conflict = raw(event, order + 1, held); send(CREATED, conflict); assertReceived(dlt, conflict);
        }
        assertThat(receipts.count()).isEqualTo(1); assertStock(f, 3, 0);
        UUID refunded = UUID.randomUUID(); String refund = raw(refunded, order, held);
        send(REFUND, refund); await(() -> receipts.existsById(refunded)); send(REFUND, refund); assertStock(f, 5, 0);
        UUID cancellationHold = UUID.randomUUID(); inventory.reserve(command(f, cancellationHold, order + 2));
        UUID cancelled = UUID.randomUUID(); send(CANCELLED, raw(cancelled, order + 2, cancellationHold));
        await(() -> receipts.existsById(cancelled)); assertStock(f, 5, 0);

        // Fail after mutating the managed reservation, then hold the retry before its mutation.
        UUID retryHold = UUID.randomUUID(); inventory.reserve(command(f, retryHold, order + 3));
        UUID retryEvent = UUID.randomUUID(); var attempts = new AtomicInteger();
        var enteredRetry = new CountDownLatch(1); var allowRetry = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (attempts.incrementAndGet() == 1) {
                invocation.callRealMethod(); throw new IllegalStateException("fixture inventory write failure");
            }
            enteredRetry.countDown();
            if (!allowRetry.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("fixture retry barrier timeout");
            return invocation.callRealMethod();
        }).when(store).changeReservationState(eq(retryHold), any(InventoryReservation.State.class));
        try {
            send(CREATED, raw(retryEvent, order + 3, retryHold));
            assertThat(enteredRetry.await(20, TimeUnit.SECONDS)).isTrue();
            assertThat(receipts.existsById(retryEvent)).isFalse(); assertStock(f, 5, 2);
            assertThat(reservations.findById(retryHold).orElseThrow().getState()).isEqualTo(MenuItemInventoryReservation.State.RESERVED);
            allowRetry.countDown(); await(() -> receipts.existsById(retryEvent)); assertStock(f, 3, 0);
            assertThat(receipts.findById(retryEvent).orElseThrow().getSourceTopic()).isEqualTo(CREATED);
            assertThat(attempts.get()).isEqualTo(2);
        } finally { allowRetry.countDown(); reset(store); }

        try (var search = consumer("entity-sync", "restaurant-search-proof")) {
            lifecycle.changeRestaurant(f.restaurant(), RestaurantStatus.PAUSED, 0L, 100L, 200L, "SHOP_OWNER");
            await(() -> outbox.findAll().stream().anyMatch(row -> row.getAggregateId().equals("RESTAURANT:" + f.restaurant())
                    && row.getStatus() == RestaurantOutboxEvent.Status.SENT));
            boolean observed = false; long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (!observed && System.nanoTime() < deadline) for (var record : search.poll(Duration.ofMillis(200))) {
                var payload = mapper.readTree(record.value());
                if (f.restaurant().toString().equals(payload.path("entityId").asText())) {
                    assertThat(payload.path("entityType").asText()).isEqualTo("RESTAURANT");
                    assertThat(payload.path("action").asText()).isEqualTo("UPDATE");
                    assertThat(record.key()).isEqualTo(f.restaurant().toString());
                    assertThat(record.headers().lastHeader("eventId")).isNotNull(); observed = true;
                }
            }
            assertThat(observed).isTrue();
        }
        try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            await(() -> {
                try {
                    var offsets = admin.listConsumerGroupOffsets(GROUP).partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS);
                    return offsets.containsKey(new TopicPartition(CREATED, 0)) && offsets.get(new TopicPartition(CREATED, 0)).offset() >= 4;
                } catch (Exception failure) { throw new IllegalStateException(failure); }
            });
        }
        assertThat(receipts.count()).isEqualTo(4);
    }

    private Fixture fixture() {
        Restaurant row = new Restaurant(); row.setName("Kafka restaurant"); row.setCreatorId(200L); row.setOwnerPrincipalId(100L);
        row = restaurants.saveAndFlush(row);
        MenuItem menu = new MenuItem(); menu.setName("Meal"); menu.setPrice(new BigDecimal("42.50")); menu.setRestaurant(row); menu.setStatus(MenuItem.Status.AVAILABLE);
        menu = menus.saveAndFlush(menu);
        MenuItemInventory stock = new MenuItemInventory(); stock.setMenuItemId(menu.getId()); stock.setOnHandQuantity(5); stocks.saveAndFlush(stock);
        serviceability.create(row.getId(), new CreateServiceabilityZoneCommand("Coverage", SQUARE, 0, true), 100L, 200L, RestaurantActorRole.SHOP_OWNER);
        return new Fixture(row.getId(), menu.getId());
    }
    private InventoryReservationCommand command(Fixture f, UUID id, long order) {
        return new InventoryReservationCommand(id, order, 300L, 400L, f.restaurant(), List.of(new InventoryReservationLineCommand(f.menu(), 2)));
    }
    private String raw(UUID event, long order, UUID reservation) throws Exception {
        return mapper.writeValueAsString(Map.of("eventId", event, "orderId", order, "inventoryReservationId", reservation));
    }
    private void send(String topic, String payload) throws Exception { kafka.send(topic, "fixture", payload).get(10, TimeUnit.SECONDS); }
    private void assertStock(Fixture f, int onHand, int reserved) {
        var stock = stocks.findById(f.menu()).orElseThrow(); assertThat(stock.getOnHandQuantity()).isEqualTo(onHand); assertThat(stock.getReservedQuantity()).isEqualTo(reserved);
    }
    private KafkaConsumer<String, String> consumer(String topic, String group) {
        var result = new KafkaConsumer<String, String>(Map.of("bootstrap.servers", KAFKA.getBootstrapServers(), "group.id", group,
                "key.deserializer", StringDeserializer.class.getName(), "value.deserializer", StringDeserializer.class.getName(), "auto.offset.reset", "earliest"));
        result.subscribe(List.of(topic)); return result;
    }
    private void assertReceived(KafkaConsumer<String, String> consumer, String expected) {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) for (var record : consumer.poll(Duration.ofMillis(200))) if (expected.equals(record.value())) return;
        throw new AssertionError("Expected payload did not reach owner DLT");
    }
    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) { if (condition.getAsBoolean()) return; Thread.sleep(50); }
        throw new AssertionError("Restaurant Kafka/Postgres state did not converge");
    }
    private record Fixture(Long restaurant, Long menu) { }
    @TestConfiguration static class Topics {
        @Bean NewTopic created() { return new NewTopic(CREATED, 1, (short) 1); }
        @Bean NewTopic cancelled() { return new NewTopic(CANCELLED, 1, (short) 1); }
        @Bean NewTopic refund() { return new NewTopic(REFUND, 1, (short) 1); }
        @Bean NewTopic search() { return new NewTopic("entity-sync", 1, (short) 1); }
    }
}
