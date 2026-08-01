package com.delivery.promotion_service.service;

import com.delivery.promotion_service.dto.ReserveRequest;
import com.delivery.promotion_service.entity.PromotionOutboxEvent;
import com.delivery.promotion_service.entity.UserVoucher;
import com.delivery.promotion_service.entity.Voucher;
import com.delivery.promotion_service.repository.PromotionOutboxEventRepository;
import com.delivery.promotion_service.repository.UserVoucherRepository;
import com.delivery.promotion_service.repository.VoucherRepository;
import com.delivery.promotion_service.repository.VoucherReservationRepository;
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
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class VoucherReservationPostgresConcurrencyTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("promotion_test")
            .withUsername("promotion")
            .withPassword("promotion");

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

    @Autowired PromotionService service;
    @Autowired VoucherRepository voucherRepository;
    @Autowired UserVoucherRepository userVoucherRepository;
    @Autowired VoucherReservationRepository reservationRepository;
    @Autowired PromotionOutboxEventRepository outboxRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private Long voucherId;

    @BeforeEach
    void seed() {
        outboxRepository.deleteAll();
        reservationRepository.deleteAll();
        userVoucherRepository.deleteAll();
        voucherRepository.deleteAll();
        Voucher voucher = voucherRepository.saveAndFlush(Voucher.builder()
                .code("LAST_ONE").name("Last one")
                .creatorType(Voucher.CreatorType.PLATFORM)
                .rewardType(Voucher.RewardType.FIXED)
                .discountValue(new BigDecimal("10000"))
                .scopeType(Voucher.ScopeType.ALL)
                .totalQuantity(1).usedQuantity(0).usageLimitPerUser(1)
                .startTime(LocalDateTime.now().minusMinutes(1))
                .endTime(LocalDateTime.now().plusHours(1))
                .minOrderValue(BigDecimal.ZERO).active(true).build());
        voucherId = voucher.getId();
        userVoucherRepository.saveAndFlush(UserVoucher.builder()
                .userId(7L).voucherId(voucherId).status(UserVoucher.Status.SAVED).build());
    }

    @Test
    void simultaneousCheckoutCannotDoubleUseTheSameWalletVoucher() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> reserveAfter(start, 101L));
            Future<Boolean> second = executor.submit(() -> reserveAfter(start, 102L));
            start.countDown();

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
            assertThat(reservationRepository.count()).isEqualTo(1);
            assertThat(voucherRepository.findById(voucherId).orElseThrow().getUsedQuantity()).isEqualTo(1);
            assertThat(userVoucherRepository.findByUserIdAndVoucherId(7L, voucherId).orElseThrow().getStatus())
                    .isEqualTo(UserVoucher.Status.RESERVED);
            assertThat(outboxRepository.findAll()).singleElement().extracting(PromotionOutboxEvent::getEventType)
                    .isEqualTo("VOUCHER_RESERVATION_RESERVED");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void terminalReplayAndCompensatingReleaseAreIdempotent() {
        UUID reservationId = UUID.randomUUID();
        ReserveRequest request = request(reservationId, 103L);

        service.reserveVoucher(request);
        service.commitReservation(reservationId, 103L);
        service.commitReservation(reservationId, 103L);
        service.releaseReservation(reservationId, 103L);
        service.releaseReservation(reservationId, 103L);
        var replay = service.reserveVoucher(request);

        assertThat(replay.getState()).isEqualTo(com.delivery.promotion_service.entity.VoucherReservation.State.RELEASED);
        assertThat(voucherRepository.findById(voucherId).orElseThrow().getUsedQuantity()).isZero();
        assertThat(userVoucherRepository.findByUserIdAndVoucherId(7L, voucherId).orElseThrow().getStatus())
                .isEqualTo(UserVoucher.Status.SAVED);
        assertThat(reservationRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(3);
    }

    @Test
    void expiredReservationRestoresVoucherExactlyOnce() {
        UUID reservationId = UUID.randomUUID();
        service.reserveVoucher(request(reservationId, 104L));
        jdbcTemplate.update("UPDATE voucher_reservations SET expires_at = ? WHERE reservation_id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusSeconds(1)), reservationId);

        assertThat(service.expireReservations()).isEqualTo(1);
        assertThat(service.expireReservations()).isZero();

        assertThat(reservationRepository.findById(reservationId).orElseThrow().getState())
                .isEqualTo(com.delivery.promotion_service.entity.VoucherReservation.State.EXPIRED);
        assertThat(voucherRepository.findById(voucherId).orElseThrow().getUsedQuantity()).isZero();
        assertThat(userVoucherRepository.findByUserIdAndVoucherId(7L, voucherId).orElseThrow().getStatus())
                .isEqualTo(UserVoucher.Status.SAVED);
        assertThat(outboxRepository.count()).isEqualTo(2);
    }

    private ReserveRequest request(UUID reservationId, long orderId) {
        return ReserveRequest.builder()
                .reservationId(reservationId).orderId(orderId).userId(7L)
                .voucherId(voucherId).restaurantId(9L)
                .subtotal(new BigDecimal("100000")).shippingFee(new BigDecimal("15000"))
                .build();
    }

    private boolean reserveAfter(CountDownLatch start, long orderId) throws InterruptedException {
        start.await();
        try {
            service.reserveVoucher(ReserveRequest.builder()
                    .reservationId(UUID.randomUUID()).orderId(orderId).userId(7L)
                    .voucherId(voucherId).restaurantId(9L)
                    .subtotal(new BigDecimal("100000")).shippingFee(new BigDecimal("15000"))
                    .build());
            return true;
        } catch (RuntimeException expectedConflict) {
            return false;
        }
    }
}
