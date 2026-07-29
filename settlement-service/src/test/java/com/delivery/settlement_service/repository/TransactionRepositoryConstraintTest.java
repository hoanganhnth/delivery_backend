package com.delivery.settlement_service.repository;

import com.delivery.settlement_service.entity.EntityType;
import com.delivery.settlement_service.entity.Transaction;
import com.delivery.settlement_service.entity.Transaction.TransactionDirection;
import com.delivery.settlement_service.entity.Transaction.TransactionReason;
import com.delivery.settlement_service.entity.Transaction.TransactionStatus;
import com.delivery.settlement_service.entity.Transaction.WalletType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class TransactionRepositoryConstraintTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private BalanceRepository balanceRepository;

    @Test
    void duplicateOrderLedgerEntryIsRejectedByDatabase() {
        transactionRepository.saveAndFlush(entry());

        assertThatThrownBy(() -> transactionRepository.saveAndFlush(entry()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void balanceCanBeLoadedWithWriteLock() {
        var balance = com.delivery.settlement_service.entity.Balance.builder()
                .entityId(22L)
                .entityType(EntityType.SHIPPER)
                .build();
        balanceRepository.saveAndFlush(balance);

        assertThat(balanceRepository.findByEntityIdAndEntityTypeForUpdate(22L, EntityType.SHIPPER))
                .isPresent();
    }

    private Transaction entry() {
        return Transaction.builder()
                .entityId(22L)
                .entityType(EntityType.SHIPPER)
                .orderId(101L)
                .direction(TransactionDirection.DEBIT)
                .reason(TransactionReason.COD_SETTLEMENT)
                .amount(new BigDecimal("120000"))
                .status(TransactionStatus.COMPLETED)
                .walletType(WalletType.DEPOSIT)
                .build();
    }
}
