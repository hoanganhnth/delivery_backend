package com.delivery.order_service.repository;

import com.delivery.order_service.service.CheckoutFingerprintService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** Proves the production PostgreSQL ON CONFLICT fence under concurrent claims. */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class OrderCreateIdempotencyPostgresConcurrencyTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("order_idempotency")
            .withUsername("order")
            .withPassword("order");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
    }

    @Autowired private OrderCreateIdempotencyReceiptRepository repository;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void concurrentSamePrincipalAndKeyCreateExactlyOneReceipt() throws Exception {
        UUID key = UUID.randomUUID();
        CyclicBarrier startTogether = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> claim(startTogether, key));
            Future<Integer> second = executor.submit(() -> claim(startTogether, key));

            List<Integer> results = List.of(first.get(), second.get());
            assertThat(results).containsExactlyInAnyOrder(1, 0);
            assertThat(repository.findByPrincipalIdAndIdempotencyKey(77L, key)).isPresent();
        } finally {
            executor.shutdownNow();
        }
    }

    private Integer claim(CyclicBarrier startTogether, UUID key) throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> {
            try {
                startTogether.await();
                return repository.insertIfAbsentPostgres(77L, key, "fingerprint",
                        CheckoutFingerprintService.VERSION);
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        });
    }
}
