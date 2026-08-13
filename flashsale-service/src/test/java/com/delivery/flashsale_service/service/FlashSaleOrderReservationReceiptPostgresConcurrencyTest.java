package com.delivery.flashsale_service.service;

import com.delivery.flashsale_service.dto.FlashSaleReservationRequest;
import com.delivery.flashsale_service.dto.ReserveItemRequest;
import com.delivery.flashsale_service.entity.FlashSaleCampaign;
import com.delivery.flashsale_service.entity.FlashSaleItem;
import com.delivery.flashsale_service.entity.FlashSaleOutboxEvent;
import com.delivery.flashsale_service.entity.FlashSaleReservation;
import com.delivery.flashsale_service.repository.FlashSaleCampaignRepository;
import com.delivery.flashsale_service.repository.FlashSaleItemRepository;
import com.delivery.flashsale_service.repository.FlashSaleOrderReservationReceiptRepository;
import com.delivery.flashsale_service.repository.FlashSaleOutboxEventRepository;
import com.delivery.flashsale_service.repository.FlashSaleReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalTime;
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
class FlashSaleOrderReservationReceiptPostgresConcurrencyTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("flashsale_receipt").withUsername("flashsale").withPassword("flashsale");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        registry.add("app.flashsale.checkout-enabled", () -> "true");
        registry.add("app.flashsale.outbox-relay-enabled", () -> "false");
        registry.add("app.flashsale.reservation-expiry-scan-ms", () -> "3600000");
        registry.add("spring.task.scheduling.enabled", () -> "false");
        registry.add("restaurant.service.url", () -> "http://127.0.0.1:1");
    }

    @Autowired private FlashSaleStockService stockService;
    @Autowired private FlashSaleOrderReservationEventProcessor processor;
    @Autowired private FlashSaleOrderReservationReceiptRepository receiptRepository;
    @Autowired private FlashSaleOutboxEventRepository outboxRepository;
    @Autowired private FlashSaleReservationRepository reservationRepository;
    @Autowired private FlashSaleItemRepository itemRepository;
    @Autowired private FlashSaleCampaignRepository campaignRepository;

    private UUID reservationId;

    @BeforeEach
    void seedReservation() {
        receiptRepository.deleteAll();
        outboxRepository.deleteAll();
        reservationRepository.deleteAll();
        itemRepository.deleteAll();
        campaignRepository.deleteAll();

        FlashSaleCampaign campaign = campaignRepository.saveAndFlush(FlashSaleCampaign.builder()
                .name("Receipt").isRecurring(false).startTime(LocalTime.MIN)
                .endTime(LocalTime.of(23, 59, 59))
                .status(FlashSaleCampaign.CampaignStatus.ACTIVE).adminId(1L).build());
        Long itemId = itemRepository.saveAndFlush(FlashSaleItem.builder().campaign(campaign)
                .restaurantId(9L).menuItemId(91L).originalPrice(new BigDecimal("100000"))
                .flashSalePrice(new BigDecimal("50000")).stockQuantity(10).soldQuantity(0)
                .status(FlashSaleItem.ItemStatus.APPROVED).build()).getId();
        reservationId = UUID.randomUUID();
        stockService.reserveStock(request(reservationId, 970001L, itemId));
    }

    @Test
    void concurrentExactCommitEventsLeaveOneReceiptAndOneTerminalOutbox() throws Exception {
        UUID eventId = UUID.randomUUID();
        String payload = payload(eventId, 970001L, reservationId);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> processAfter(start, payload));
            Future<?> second = executor.submit(() -> processAfter(start, payload));
            start.countDown();
            first.get();
            second.get();
        } finally {
            executor.shutdownNow();
        }

        assertThat(receiptRepository.count()).isEqualTo(1);
        assertThat(reservationRepository.findById(reservationId).orElseThrow().getState())
                .isEqualTo(FlashSaleReservation.State.COMMITTED);
        assertThat(outboxRepository.findAll()).extracting(FlashSaleOutboxEvent::getEventType)
                .containsExactlyInAnyOrder("FLASH_SALE_RESERVATION_RESERVED", "FLASH_SALE_RESERVATION_COMMITTED");
    }

    @Test
    void contradictoryReuseDoesNotChangeTheCommittedReservation() throws Exception {
        UUID eventId = UUID.randomUUID();
        processor.process(payload(eventId, 970001L, reservationId), "order.created");

        assertThatThrownBy(() -> processor.process(payload(eventId, 970002L, reservationId), "order.created"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contradictory flash-sale reservation payload");

        assertThat(receiptRepository.count()).isEqualTo(1);
        assertThat(reservationRepository.findById(reservationId).orElseThrow().getState())
                .isEqualTo(FlashSaleReservation.State.COMMITTED);
        assertThat(outboxRepository.count()).isEqualTo(2);
    }

    @Test
    void failedStockTransitionRollsBackItsReceiptForKafkaReplay() {
        UUID eventId = UUID.randomUUID();
        UUID absentReservation = UUID.randomUUID();

        assertThatThrownBy(() -> processor.process(payload(eventId, 970001L, absentReservation), "order.created"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Flash sale reservation not found");

        assertThat(receiptRepository.findById(eventId)).isEmpty();
    }

    private FlashSaleReservationRequest request(UUID id, long orderId, long itemId) {
        ReserveItemRequest line = new ReserveItemRequest();
        line.setFlashSaleItemId(itemId);
        line.setQuantity(1);
        FlashSaleReservationRequest request = new FlashSaleReservationRequest();
        request.setReservationId(id);
        request.setOrderId(orderId);
        request.setUserId(7L);
        request.setRestaurantId(9L);
        request.setItems(List.of(line));
        return request;
    }

    private void processAfter(CountDownLatch start, String payload) {
        try {
            start.await();
            processor.process(payload, "order.created");
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private String payload(UUID eventId, long orderId, UUID reservation) {
        return "{\"eventId\":\"" + eventId + "\",\"orderId\":" + orderId
                + ",\"flashSaleReservationId\":\"" + reservation + "\"}";
    }
}
