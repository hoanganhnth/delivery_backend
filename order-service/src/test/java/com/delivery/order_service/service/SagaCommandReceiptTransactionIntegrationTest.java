package com.delivery.order_service.service;

import com.delivery.order_service.repository.SagaCommandReceiptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class SagaCommandReceiptTransactionIntegrationTest {

    @Autowired SagaCommandReceiptRepository repository;
    @Autowired SagaCommandReceiptService receipts;
    @Autowired TransactionTemplate transactionTemplate;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void claimCommitsWithTheEnclosingOrderCommandTransaction() {
        UUID eventId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status ->
                receipts.claim(eventId, SagaCommandReceiptService.UPDATE_ORDER_STATUS, 101L,
                        "FINDING_SHIPPER", "{\"eventId\":\"" + eventId + "\"}"));

        assertThat(repository.findById(eventId)).isPresent();
    }

    @Test
    void rollbackAfterClaimLeavesTheKafkaCommandReplayable() {
        UUID eventId = UUID.randomUUID();
        String payload = "{\"eventId\":\"" + eventId + "\"}";

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            receipts.claim(eventId, SagaCommandReceiptService.UPDATE_ORDER_STATUS, 101L,
                    "FINDING_SHIPPER", payload);
            throw new DeliberateRollback();
        })).isInstanceOf(DeliberateRollback.class);

        assertThat(repository.findById(eventId)).isEmpty();
        assertThat(receipts.claim(eventId, SagaCommandReceiptService.UPDATE_ORDER_STATUS,
                101L, "FINDING_SHIPPER", payload)).isTrue();
    }

    private static final class DeliberateRollback extends RuntimeException {
    }
}
