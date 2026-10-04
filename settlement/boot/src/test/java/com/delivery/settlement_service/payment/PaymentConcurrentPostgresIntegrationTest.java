package com.delivery.settlement_service.payment;

import com.delivery.settlement.application.api.*;
import com.delivery.settlement_service.SettlementServiceApplication;
import com.delivery.settlement_service.entity.*;
import com.delivery.settlement_service.repository.*;
import com.delivery.settlement_service.service.PaymentEventPublisher;
import com.delivery.settlement_service.service.TransactionService;
import java.math.BigDecimal;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real PostgreSQL transaction race: the first callback is held inside its top-up while a second identical
 * callback runs. The second must observe the committed SUCCESS instead of posting another deposit. */
@SpringBootTest(classes = SettlementServiceApplication.class, properties = {
        "app.payment.processing-enabled=true", "app.settlement.application-api-enabled=false",
        "payment.vnpay.tmn-code=test-merchant", "payment.vnpay.hash-secret=ipn-proof-secret",
        "spring.task.scheduling.enabled=false", "spring.flyway.enabled=false"})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class PaymentConcurrentPostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        properties.add("spring.datasource.hikari.maximum-pool-size", () -> "6");
    }
    @Autowired PaymentWorkflowPort workflow;
    @Autowired PaymentOrderRepository payments;
    @SpyBean TransactionService ledger;
    @Autowired TransactionRepository transactions;
    @Autowired BalanceRepository balances;
    @MockBean PaymentEventPublisher events;
    @Test void concurrentPendingTopUpCallbacksReportLedgerWalletAndEventCounts() throws Exception {
        payments.deleteAll(); transactions.deleteAll(); balances.deleteAll();
        // Avoid the unrelated first-account creation race; balance posting still uses real row locks.
        balances.saveAndFlush(Balance.builder().entityId(55L).entityType(EntityType.SHIPPER).build());
        var created = workflow.create(new PaymentWorkflowCommand(55L, null, "SHIPPER", new BigDecimal("100"),
                "VNPAY", "DEPOSIT_TOPUP", null, null));
        var params = PaymentSignedIpnIntegrationTest.signed(created.paymentReference(), "10000");
        var firstToppingUp = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            firstToppingUp.countDown();
            release.await(20, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(ledger).topUpDeposit(eq(55L), eq(EntityType.SHIPPER), any(), eq("VNPAY"));
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> workflow.callback("VNPAY", params));
            assertThat(firstToppingUp.await(20, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> workflow.callback("VNPAY", params));
            // Without a payment row lock the second callback reads PENDING within this window.
            Thread.sleep(1500);
            release.countDown();
            assertThat(first.get(30, TimeUnit.SECONDS).status()).isEqualTo("SUCCESS");
            assertThat(second.get(30, TimeUnit.SECONDS).status()).isEqualTo("SUCCESS");
            long ledgerCount = transactions.count();
            var walletIncrement = balances.findByEntityIdAndEntityType(55L, EntityType.SHIPPER).orElseThrow().getDepositBalance();
            long eventCount = mockingDetails(events).getInvocations().stream()
                    .filter(invocation -> invocation.getMethod().getName().equals("publishPaymentSuccess")).count();
            System.out.printf("PAYMENT_CONCURRENCY_OBSERVED ledger=%d walletIncrement=%s events=%d%n",
                    ledgerCount, walletIncrement, eventCount);
            assertThat(ledgerCount).as("observed ledger entries").isEqualTo(1);
            assertThat(walletIncrement).as("observed wallet increment").isEqualByComparingTo("100");
            assertThat(eventCount).as("observed success publications").isEqualTo(1);
        } finally {
            executor.shutdownNow(); reset(ledger);
            payments.deleteAll(); transactions.deleteAll(); balances.deleteAll();
        }
    }
}
