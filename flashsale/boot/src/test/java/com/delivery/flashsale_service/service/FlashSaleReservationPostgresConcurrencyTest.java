package com.delivery.flashsale_service.service;

import com.delivery.flashsale_service.dto.FlashSaleReservationRequest;
import com.delivery.flashsale_service.dto.ReserveItemRequest;
import com.delivery.flashsale_service.entity.FlashSaleCampaign;
import com.delivery.flashsale_service.entity.FlashSaleItem;
import com.delivery.flashsale_service.repository.FlashSaleCampaignRepository;
import com.delivery.flashsale_service.repository.FlashSaleItemRepository;
import com.delivery.flashsale_service.repository.FlashSaleReservationRepository;
import com.delivery.flashsale_service.repository.FlashSaleOutboxEventRepository;
import com.delivery.flashsale_service.entity.FlashSaleOutboxEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class FlashSaleReservationPostgresConcurrencyTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("flashsale_test").withUsername("flashsale").withPassword("flashsale");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.flashsale.checkout-enabled", () -> "true");
        registry.add("app.flashsale.reservation-expiry-scan-ms", () -> "3600000");
        registry.add("spring.task.scheduling.enabled", () -> "false");
        registry.add("restaurant.service.url", () -> "http://127.0.0.1:1");
    }

    @Autowired FlashSaleStockService stockService;
    @Autowired FlashSaleReservationRepository reservationRepository;
    @Autowired FlashSaleItemRepository itemRepository;
    @Autowired FlashSaleCampaignRepository campaignRepository;
    @Autowired FlashSaleOutboxEventRepository outboxRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    private Long itemId;

    @BeforeEach
    void seed() {
        outboxRepository.deleteAll();
        reservationRepository.deleteAll();
        itemRepository.deleteAll();
        campaignRepository.deleteAll();
        FlashSaleCampaign campaign = campaignRepository.saveAndFlush(FlashSaleCampaign.builder()
                .name("All day").isRecurring(false).startTime(LocalTime.MIN)
                .endTime(LocalTime.of(23, 59, 59))
                .status(FlashSaleCampaign.CampaignStatus.ACTIVE).adminId(1L).build());
        itemId = itemRepository.saveAndFlush(FlashSaleItem.builder().campaign(campaign)
                .restaurantId(9L).menuItemId(91L).originalPrice(new BigDecimal("100000"))
                .flashSalePrice(new BigDecimal("50000")).stockQuantity(1).soldQuantity(0)
                .status(FlashSaleItem.ItemStatus.APPROVED).build()).getId();
    }

    @Test
    void lastStockCanOnlyBeReservedByOneConcurrentOrder() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> reserve(start, 101L, 7L));
            Future<String> second = executor.submit(() -> reserve(start, 102L, 8L));
            start.countDown();
            List<String> results = List.of(first.get(), second.get());
            assertThat(results.stream().filter("OK"::equals).count())
                    .as("one reservation must succeed; results=%s", results).isEqualTo(1);
            assertThat(reservationRepository.count()).isEqualTo(1);
            assertThat(itemRepository.findById(itemId).orElseThrow().getSoldQuantity()).isEqualTo(1);
            assertThat(outboxRepository.findAll()).singleElement()
                    .extracting(FlashSaleOutboxEvent::getEventType)
                    .isEqualTo("FLASH_SALE_RESERVATION_RESERVED");
        } finally { executor.shutdownNow(); }
    }

    @Test
    void exhaustedSecondLineLeavesEveryLineAndOutboxUnchanged() {
        FlashSaleCampaign campaign = campaignRepository.findAll().get(0);
        Long exhaustedItemId = itemRepository.saveAndFlush(FlashSaleItem.builder().campaign(campaign)
                .restaurantId(9L).menuItemId(92L).originalPrice(new BigDecimal("80000"))
                .flashSalePrice(new BigDecimal("40000")).stockQuantity(1).soldQuantity(1)
                .status(FlashSaleItem.ItemStatus.APPROVED).build()).getId();

        ReserveItemRequest available = new ReserveItemRequest();
        available.setFlashSaleItemId(itemId); available.setQuantity(1);
        ReserveItemRequest exhausted = new ReserveItemRequest();
        exhausted.setFlashSaleItemId(exhaustedItemId); exhausted.setQuantity(1);
        FlashSaleReservationRequest request = new FlashSaleReservationRequest();
        request.setReservationId(UUID.randomUUID()); request.setOrderId(103L);
        request.setUserId(9L); request.setRestaurantId(9L);
        request.setItems(List.of(available, exhausted));

        assertThatThrownBy(() -> stockService.reserveStock(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Out of stock");

        assertThat(itemRepository.findById(itemId).orElseThrow().getSoldQuantity()).isZero();
        assertThat(itemRepository.findById(exhaustedItemId).orElseThrow().getSoldQuantity()).isEqualTo(1);
        assertThat(reservationRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void terminalReplayAndCompensatingReleaseAreIdempotent() {
        UUID reservationId = UUID.randomUUID();
        FlashSaleReservationRequest request = request(reservationId, 104L, 10L, itemId);

        stockService.reserveStock(request);
        stockService.commit(reservationId, 104L);
        stockService.commit(reservationId, 104L);
        stockService.release(reservationId, 104L);
        stockService.release(reservationId, 104L);
        var replay = stockService.reserveStock(request);

        assertThat(replay.getState()).isEqualTo(com.delivery.flashsale_service.entity.FlashSaleReservation.State.RELEASED);
        assertThat(itemRepository.findById(itemId).orElseThrow().getSoldQuantity()).isZero();
        assertThat(reservationRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(3);
    }

    @Test
    void expiredReservationRestoresStockExactlyOnce() {
        UUID reservationId = UUID.randomUUID();
        stockService.reserveStock(request(reservationId, 105L, 11L, itemId));
        jdbcTemplate.update("UPDATE flash_sale_reservations SET expires_at = ? WHERE reservation_id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusSeconds(1)), reservationId);

        assertThat(stockService.expireReservations()).isEqualTo(1);
        assertThat(stockService.expireReservations()).isZero();

        assertThat(reservationRepository.findById(reservationId).orElseThrow().getState())
                .isEqualTo(com.delivery.flashsale_service.entity.FlashSaleReservation.State.EXPIRED);
        assertThat(itemRepository.findById(itemId).orElseThrow().getSoldQuantity()).isZero();
        assertThat(outboxRepository.count()).isEqualTo(2);
    }

    private FlashSaleReservationRequest request(UUID reservationId, long orderId, long userId, long id) {
        ReserveItemRequest line = new ReserveItemRequest();
        line.setFlashSaleItemId(id); line.setQuantity(1);
        FlashSaleReservationRequest request = new FlashSaleReservationRequest();
        request.setReservationId(reservationId); request.setOrderId(orderId);
        request.setUserId(userId); request.setRestaurantId(9L); request.setItems(List.of(line));
        return request;
    }

    private String reserve(CountDownLatch start, long orderId, long userId) throws InterruptedException {
        start.await();
        ReserveItemRequest line = new ReserveItemRequest();
        line.setFlashSaleItemId(itemId); line.setQuantity(1);
        FlashSaleReservationRequest request = new FlashSaleReservationRequest();
        request.setReservationId(UUID.randomUUID()); request.setOrderId(orderId);
        request.setUserId(userId); request.setRestaurantId(9L); request.setItems(List.of(line));
        try { stockService.reserveStock(request); return "OK"; }
        catch (RuntimeException expected) { return expected.getClass().getSimpleName() + ":" + expected.getMessage(); }
    }
}
