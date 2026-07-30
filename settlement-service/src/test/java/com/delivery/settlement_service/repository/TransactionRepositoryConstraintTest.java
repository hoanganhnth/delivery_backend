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
import org.springframework.data.domain.PageRequest;

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

    @Test
    void pendingWithdrawalQueryFiltersInDatabaseBeforeApplyingLimit() {
        transactionRepository.saveAndFlush(entry(201L, TransactionReason.ADJUSTMENT_DEBIT));
        transactionRepository.saveAndFlush(entry(202L, TransactionReason.WITHDRAW));

        var withdrawals = transactionRepository.findByStatusAndReasonOrderByCreatedAtDesc(
                TransactionStatus.PENDING, TransactionReason.WITHDRAW, PageRequest.of(0, 1));

        assertThat(withdrawals).singleElement()
                .satisfies(transaction -> assertThat(transaction.getReason())
                        .isEqualTo(TransactionReason.WITHDRAW));
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

    private Transaction entry(long orderId, TransactionReason reason) {
        return Transaction.builder()
                .entityId(22L)
                .entityType(EntityType.SHIPPER)
                .orderId(orderId)
                .direction(TransactionDirection.DEBIT)
                .reason(reason)
                .amount(new BigDecimal("1000"))
                .status(TransactionStatus.PENDING)
                .walletType(WalletType.EARNINGS)
                .build();
    }
}
