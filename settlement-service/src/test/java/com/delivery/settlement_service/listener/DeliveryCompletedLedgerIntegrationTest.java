package com.delivery.settlement_service.listener;

import com.delivery.settlement_service.dto.event.DeliveryCompletedEvent;
import com.delivery.settlement_service.entity.Balance;
import com.delivery.settlement_service.entity.EntityType;
import com.delivery.settlement_service.repository.BalanceRepository;
import com.delivery.settlement_service.repository.SettlementReceiptRepository;
import com.delivery.settlement_service.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class DeliveryCompletedLedgerIntegrationTest {

    @Autowired
    private DeliveryCompletedEventListener listener;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private BalanceRepository balanceRepository;

    @Autowired
    private SettlementReceiptRepository settlementReceiptRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    @AfterEach
    void cleanLedger() {
        settlementReceiptRepository.deleteAll();
        transactionRepository.deleteAll();
        balanceRepository.deleteAll();
    }

    @Test
    void replayKeepsOneBalancedCodPosting() throws Exception {
        var acknowledgment = mock(Acknowledgment.class);
        balanceRepository.save(Balance.builder()
                .entityId(22L)
                .entityType(EntityType.SHIPPER)
                .depositBalance(new BigDecimal("120000"))
                .totalDeposited(new BigDecimal("120000"))
                .build());
        var event = DeliveryCompletedEvent.builder()
                .eventId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .eventType("DELIVERY_COMPLETED")
                .deliveryId(1L)
                .orderId(101L)
                .restaurantId(11L)
                .shipperId(22L)
                .restaurantEarnings(new BigDecimal("80000"))
                .restaurantCommission(new BigDecimal("20000"))
                .shippingFee(new BigDecimal("20000"))
                .totalPrice(new BigDecimal("120000"))
                .shipperEarnings(new BigDecimal("17000"))
                .shippingCommission(new BigDecimal("3000"))
                .totalPlatformEarnings(new BigDecimal("23000"))
                .paymentMethod("COD")
                .build();
        String payload = new ObjectMapper().findAndRegisterModules().writeValueAsString(event);

        listener.handleDeliveryCompleted(payload, "delivery.completed", 0, 1L, acknowledgment);
        listener.handleDeliveryCompleted(payload, "delivery.completed", 0, 2L, acknowledgment);

        assertThat(transactionRepository.count()).isEqualTo(4);
        assertThat(balanceRepository.findByEntityIdAndEntityType(11L, EntityType.RESTAURANT))
                .get().extracting(balance -> balance.getAvailableBalance())
                .isEqualTo(new BigDecimal("80000.00"));
        assertThat(balanceRepository.findByEntityIdAndEntityType(22L, EntityType.SHIPPER))
                .get().satisfies(balance -> {
                    assertThat(balance.getAvailableBalance()).isEqualByComparingTo("17000");
                    assertThat(balance.getDepositBalance()).isEqualByComparingTo("0");
                    assertThat(balance.getTotalCodCollected()).isEqualByComparingTo("120000");
                });
        assertThat(balanceRepository.findByEntityIdAndEntityType(0L, EntityType.SYSTEM))
                .get().extracting(balance -> balance.getAvailableBalance())
                .isEqualTo(new BigDecimal("23000.00"));
        verify(acknowledgment, times(2)).acknowledge();
    }

    @Test
    void insufficientDepositRollsBackReceiptAndLedgerInsteadOfCreatingNegativeBalance() throws Exception {
        var acknowledgment = mock(Acknowledgment.class);
        balanceRepository.save(Balance.builder()
                .entityId(22L)
                .entityType(EntityType.SHIPPER)
                .depositBalance(new BigDecimal("119999"))
                .totalDeposited(new BigDecimal("119999"))
                .build());
        var event = DeliveryCompletedEvent.builder()
                .eventId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                .eventType("DELIVERY_COMPLETED")
                .deliveryId(2L)
                .orderId(102L)
                .restaurantId(11L)
                .shipperId(22L)
                .restaurantEarnings(new BigDecimal("80000"))
                .restaurantCommission(new BigDecimal("20000"))
                .shippingFee(new BigDecimal("20000"))
                .totalPrice(new BigDecimal("120000"))
                .shipperEarnings(new BigDecimal("17000"))
                .shippingCommission(new BigDecimal("3000"))
                .totalPlatformEarnings(new BigDecimal("23000"))
                .paymentMethod("COD")
                .build();
        String payload = new ObjectMapper().findAndRegisterModules().writeValueAsString(event);

        assertThatThrownBy(() -> listener.handleDeliveryCompleted(
                payload, "delivery.completed", 0, 1L, acknowledgment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to settle delivery");

        assertThat(settlementReceiptRepository.count()).isZero();
        assertThat(transactionRepository.count()).isZero();
        assertThat(balanceRepository.findByEntityIdAndEntityType(22L, EntityType.SHIPPER))
                .get().satisfies(balance -> {
                    assertThat(balance.getDepositBalance()).isEqualByComparingTo("119999");
                    assertThat(balance.getTotalCodCollected()).isZero();
                });
        verify(acknowledgment, times(0)).acknowledge();
    }

    @Test
    void concurrentDuplicateCompletionKeepsOneReceiptAndFourLedgerRows() throws Exception {
        balanceRepository.save(Balance.builder()
                .entityId(22L)
                .entityType(EntityType.SHIPPER)
                .depositBalance(new BigDecimal("120000"))
                .totalDeposited(new BigDecimal("120000"))
                .build());
        var event = DeliveryCompletedEvent.builder()
                .eventId(UUID.fromString("33333333-3333-3333-3333-333333333333"))
                .eventType("DELIVERY_COMPLETED")
                .deliveryId(3L)
                .orderId(103L)
                .restaurantId(11L)
                .shipperId(22L)
                .restaurantEarnings(new BigDecimal("80000"))
                .restaurantCommission(new BigDecimal("20000"))
                .shippingFee(new BigDecimal("20000"))
                .totalPrice(new BigDecimal("120000"))
                .shipperEarnings(new BigDecimal("17000"))
                .shippingCommission(new BigDecimal("3000"))
                .totalPlatformEarnings(new BigDecimal("23000"))
                .paymentMethod("COD")
                .build();
        String payload = new ObjectMapper().findAndRegisterModules().writeValueAsString(event);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Throwable> first = submitConcurrentDelivery(payload, start, executor);
            Future<Throwable> second = submitConcurrentDelivery(payload, start, executor);
            start.countDown();

            Throwable firstFailure = first.get();
            Throwable secondFailure = second.get();
            assertThat(firstFailure == null || secondFailure == null)
                    .as("one concurrent duplicate must commit while the other replays or retries")
                    .isTrue();
            assertThat(settlementReceiptRepository.count()).isEqualTo(1);
            assertThat(transactionRepository.count()).isEqualTo(4);
            assertThat(balanceRepository.findByEntityIdAndEntityType(22L, EntityType.SHIPPER))
                    .get().satisfies(balance -> {
                        assertThat(balance.getDepositBalance()).isEqualByComparingTo("0");
                        assertThat(balance.getTotalCodCollected()).isEqualByComparingTo("120000");
                    });
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void acknowledgementWaitsUntilTheFinancialTransactionCommits() throws Exception {
        balanceRepository.save(Balance.builder()
                .entityId(22L)
                .entityType(EntityType.SHIPPER)
                .depositBalance(new BigDecimal("120000"))
                .totalDeposited(new BigDecimal("120000"))
                .build());
        var event = DeliveryCompletedEvent.builder()
                .eventId(UUID.fromString("44444444-4444-4444-4444-444444444444"))
                .eventType("DELIVERY_COMPLETED")
                .deliveryId(4L)
                .orderId(104L)
                .restaurantId(11L)
                .shipperId(22L)
                .restaurantEarnings(new BigDecimal("80000"))
                .restaurantCommission(new BigDecimal("20000"))
                .shippingFee(new BigDecimal("20000"))
                .totalPrice(new BigDecimal("120000"))
                .shipperEarnings(new BigDecimal("17000"))
                .shippingCommission(new BigDecimal("3000"))
                .totalPlatformEarnings(new BigDecimal("23000"))
                .paymentMethod("COD")
                .build();
        String payload = new ObjectMapper().findAndRegisterModules().writeValueAsString(event);
        AtomicBoolean acknowledged = new AtomicBoolean(false);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            acknowledged.set(true);
            return null;
        }).when(acknowledgment).acknowledge();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            try {
                listener.handleDeliveryCompleted(payload, "delivery.completed", 0,
                        System.currentTimeMillis(), acknowledgment);
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
            assertThat(acknowledged).as("offset must not be acknowledged before DB commit")
                    .isFalse();
        });

        assertThat(acknowledged).isTrue();
        assertThat(transactionRepository.count()).isEqualTo(4);
    }

    private Future<Throwable> submitConcurrentDelivery(
            String payload, CountDownLatch start, ExecutorService executor) {
        return executor.submit(() -> {
            start.await();
            try {
                listener.handleDeliveryCompleted(payload, "delivery.completed", 0,
                        System.currentTimeMillis(), mock(Acknowledgment.class));
                return null;
            } catch (Throwable failure) {
                return failure;
            }
        });
    }
}
