package com.delivery.promotion_service.service;

import com.delivery.promotion_service.dto.ReserveRequest;
import com.delivery.promotion_service.entity.PromotionOutboxEvent;
import com.delivery.promotion_service.entity.UserVoucher;
import com.delivery.promotion_service.entity.Voucher;
import com.delivery.promotion_service.entity.VoucherReservation;
import com.delivery.promotion_service.repository.PromotionOrderReservationReceiptRepository;
import com.delivery.promotion_service.repository.PromotionOutboxEventRepository;
import com.delivery.promotion_service.repository.UserVoucherRepository;
import com.delivery.promotion_service.repository.VoucherRepository;
import com.delivery.promotion_service.repository.VoucherReservationRepository;
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
import java.time.LocalDateTime;
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
class PromotionOrderReservationReceiptPostgresConcurrencyTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("promotion_receipt").withUsername("promotion").withPassword("promotion");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        registry.add("app.promotion.checkout-enabled", () -> "false");
    }

    @Autowired private PromotionService promotionService;
    @Autowired private PromotionOrderReservationEventProcessor processor;
    @Autowired private PromotionOrderReservationReceiptRepository receiptRepository;
    @Autowired private PromotionOutboxEventRepository outboxRepository;
    @Autowired private VoucherReservationRepository reservationRepository;
    @Autowired private UserVoucherRepository userVoucherRepository;
    @Autowired private VoucherRepository voucherRepository;

    private UUID reservationId;

    @BeforeEach
    void seedReservation() {
        receiptRepository.deleteAll();
        outboxRepository.deleteAll();
        reservationRepository.deleteAll();
        userVoucherRepository.deleteAll();
        voucherRepository.deleteAll();

        Voucher voucher = voucherRepository.saveAndFlush(Voucher.builder()
                .code("RECEIPT_" + UUID.randomUUID()).name("Receipt")
                .creatorType(Voucher.CreatorType.PLATFORM).rewardType(Voucher.RewardType.FIXED)
                .discountValue(new BigDecimal("10000")).scopeType(Voucher.ScopeType.ALL)
                .totalQuantity(10).usedQuantity(0).usageLimitPerUser(1)
                .startTime(LocalDateTime.now().minusMinutes(1)).endTime(LocalDateTime.now().plusHours(1))
                .minOrderValue(BigDecimal.ZERO).active(true).build());
        userVoucherRepository.saveAndFlush(UserVoucher.builder()
                .userId(7L).voucherId(voucher.getId()).status(UserVoucher.Status.SAVED).build());
        reservationId = UUID.randomUUID();
        promotionService.reserveVoucher(ReserveRequest.builder()
                .reservationId(reservationId).orderId(970001L).userId(7L).voucherId(voucher.getId())
                .restaurantId(9L).subtotal(new BigDecimal("100000"))
                .shippingFee(new BigDecimal("15000")).build());
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
                .isEqualTo(VoucherReservation.State.COMMITTED);
        assertThat(outboxRepository.findAll()).extracting(PromotionOutboxEvent::getEventType)
                .containsExactlyInAnyOrder("VOUCHER_RESERVATION_RESERVED", "VOUCHER_RESERVATION_COMMITTED");
    }

    @Test
    void contradictoryReuseRollsBackWithoutChangingTheCommittedReservation() throws Exception {
        UUID eventId = UUID.randomUUID();
        processor.process(payload(eventId, 970001L, reservationId), "order.created");

        assertThatThrownBy(() -> processor.process(payload(eventId, 970002L, reservationId), "order.created"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contradictory voucher reservation payload");

        assertThat(receiptRepository.count()).isEqualTo(1);
        assertThat(reservationRepository.findById(reservationId).orElseThrow().getState())
                .isEqualTo(VoucherReservation.State.COMMITTED);
        assertThat(outboxRepository.count()).isEqualTo(2);
    }

    @Test
    void failedReservationTransitionRollsBackItsReceiptForKafkaReplay() {
        UUID eventId = UUID.randomUUID();
        UUID absentReservation = UUID.randomUUID();
        assertThatThrownBy(() -> processor.process(payload(eventId, 970001L, absentReservation), "order.created"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Voucher reservation not found");

        assertThat(receiptRepository.findById(eventId)).isEmpty();
    }

    private void processAfter(CountDownLatch start, String payload) {
        try {
            start.await();
            processor.process(payload, "order.created");
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private String payload(UUID eventId, long orderId, UUID reservationId) {
        return "{\"eventId\":\"" + eventId + "\",\"orderId\":" + orderId
                + ",\"voucherReservationId\":\"" + reservationId + "\"}";
    }
}
