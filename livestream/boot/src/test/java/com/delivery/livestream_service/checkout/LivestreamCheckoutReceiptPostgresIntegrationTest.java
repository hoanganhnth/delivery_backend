package com.delivery.livestream_service.checkout;

import com.delivery.livestream_service.dto.request.LivestreamCheckoutQuoteRequest;
import com.delivery.livestream_service.entity.Livestream;
import com.delivery.livestream_service.entity.LivestreamProduct;
import com.delivery.livestream_service.enums.LivestreamStatus;
import com.delivery.livestream_service.enums.StreamProvider;
import com.delivery.livestream_service.service.LivestreamCheckoutQuoteService;
import com.delivery.livestream_service.service.LivestreamCheckoutReceiptWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

/** Runs the shared retirement/conflicting-replay cases on migrated PostgreSQL too. */
@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.kafka.listener.auto-startup=false"})
@Testcontainers(disabledWithoutDocker = true)
class LivestreamCheckoutReceiptPostgresIntegrationTest extends LivestreamCheckoutReceiptIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("livestream_receipt").withUsername("livestream").withPassword("livestream");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired ObjectMapper mapper;
    @Autowired LivestreamCheckoutReceiptWriter writer;

    @Test
    void exactConcurrentHandoffsConvergeAndFreshServiceRestoresStoredSnapshot() throws Exception {
        var room = new Livestream();
        room.setSellerId(7L); room.setRestaurantId(42L); room.setTitle("Concurrent receipt");
        room.setStatus(LivestreamStatus.LIVE); room.setStreamProvider(StreamProvider.AGORA);
        String roomKey = UUID.randomUUID().toString();
        room.setRoomId("pg-receipt-room-" + roomKey);
        room.setChannelName("pg-receipt-channel-" + roomKey);
        room = rooms.saveAndFlush(room);
        var product = new LivestreamProduct();
        product.setLivestreamId(room.getId()); product.setProductId(10L); product.setRestaurantId(42L);
        product.setIsPinned(true); product.setPriceAtLive(new BigDecimal("99000"));
        products.saveAndFlush(product);
        var request = new LivestreamCheckoutQuoteRequest();
        request.setLivestreamId(room.getId()); request.setRestaurantId(42L); request.setProductIds(List.of(10L));
        String key = UUID.randomUUID().toString();
        var start = new CountDownLatch(1);
        var ready = new CountDownLatch(8);
        var executor = Executors.newFixedThreadPool(8);
        try {
            var futures = new ArrayList<Future<Object>>();
            for (int i = 0; i < 8; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Race did not start");
                    return service.orderContext(request, 777L, "receipt-race", key);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            Object accepted = futures.get(0).get(30, TimeUnit.SECONDS);
            for (var future : futures) assertThat(future.get(30, TimeUnit.SECONDS)).isEqualTo(accepted);
            var fresh = new LivestreamCheckoutQuoteService(rooms, products, receipts, mapper, writer);
            assertThat(fresh.orderContext(request, 777L, "new-correlation", key)).isEqualTo(accepted);
            assertThat(receipts.findAll().stream().filter(receipt -> key.equals(receipt.getIdempotencyKey())).count())
                    .isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
}
