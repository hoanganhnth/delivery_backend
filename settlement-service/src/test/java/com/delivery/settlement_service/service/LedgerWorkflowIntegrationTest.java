package com.delivery.settlement_service.service;

import com.delivery.settlement_service.entity.Balance;
import com.delivery.settlement_service.entity.EntityType;
import com.delivery.settlement_service.entity.Transaction.TransactionDirection;
import com.delivery.settlement_service.entity.Transaction.TransactionReason;
import com.delivery.settlement_service.entity.Transaction.TransactionStatus;
import com.delivery.settlement_service.dto.request.RejectWithdrawalRequest;
import com.delivery.settlement_service.exception.InsufficientBalanceException;
import com.delivery.settlement_service.exception.ResourceNotFoundException;
import com.delivery.settlement_service.repository.BalanceRepository;
import com.delivery.settlement_service.repository.TransactionRepository;
import com.delivery.settlement_service.repository.SettlementReceiptRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

/** Real Spring transaction/JPA adapter proof for the existing default-off financial mutations. */
@SpringBootTest
@ActiveProfiles("test")
class LedgerWorkflowIntegrationTest {
    @Autowired TransactionService service;
    @Autowired BalanceRepository balances;
    @Autowired TransactionRepository transactions;
    @Autowired SettlementReceiptRepository receipts;
    private BigDecimal amount(String value) { return new BigDecimal(value); }
    private Balance shipper() { return balances.findByEntityIdAndEntityType(22L, EntityType.SHIPPER).orElseThrow(); }
    @BeforeEach @AfterEach void clear() {
        receipts.deleteAll();
        transactions.deleteAll();
        balances.deleteAll();
    }
    @Test void topupWithdrawalRejectAndApprovePersistSeparateWalletsAndProcessingMetadata() {
        service.topUpDeposit(22L, EntityType.SHIPPER, amount("100"), "VNPay");
        service.createTransaction(22L, EntityType.SHIPPER, null, TransactionDirection.CREDIT,
                TransactionReason.DELIVERY_FEE, amount("60"), "earnings");
        var request = service.requestWithdrawal(22L, EntityType.SHIPPER, amount("20"), 22L);
        assertThat(shipper().getAvailableBalance()).isEqualByComparingTo("40");
        assertThat(shipper().getPendingBalance()).isEqualByComparingTo("20");
        var rejection = new RejectWithdrawalRequest();
        rejection.setReason("bank unavailable");
        service.rejectWithdrawal(request.getId(), 88L, rejection);
        assertThat(shipper().getAvailableBalance()).isEqualByComparingTo("60");
        assertThat(transactions.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(TransactionStatus.FAILED);
        var next = service.requestWithdrawal(22L, EntityType.SHIPPER, amount("10"), 22L);
        service.approveWithdrawal(next.getId(), 88L);
        var approved = transactions.findById(next.getId()).orElseThrow();
        assertThat(approved.getProcessedBy()).isEqualTo(88L);
        assertThat(approved.getProcessedAt()).isNotNull();
        assertThat(approved.getCreatedAt()).isNotNull();
        assertThat(shipper().getAvailableBalance()).isEqualByComparingTo("50");
        assertThat(shipper().getPendingBalance()).isEqualByComparingTo("0");
        assertThat(shipper().getDepositBalance()).isEqualByComparingTo("100");
        assertThat(shipper().getTotalDeposited()).isEqualByComparingTo("100");
        assertThatThrownBy(() -> service.approveWithdrawal(next.getId(), 88L)).isInstanceOf(IllegalStateException.class);
        assertThat(transactions.count()).isEqualTo(4);
        assertThat(shipper().getAvailableBalance()).isEqualByComparingTo("50");
    }
    @Test void holdReleaseAndOrdinaryReversalRetainStoredDirectionAndOriginalStatus() {
        var original = service.createTransaction(22L, EntityType.SHIPPER, null, TransactionDirection.CREDIT,
                TransactionReason.DELIVERY_FEE, amount("100"), "earnings");
        service.holdBalance(22L, amount("20"), null);
        assertThat(shipper().getAvailableBalance()).isEqualByComparingTo("80");
        assertThat(shipper().getHoldingBalance()).isEqualByComparingTo("20");
        service.releaseBalance(22L, amount("20"), null);
        assertThat(shipper().getHoldingBalance()).isEqualByComparingTo("0");
        var reversed = service.reverseTransaction(original.getId(), 88L, "correction");
        assertThat(reversed.getDirection()).isEqualTo(TransactionDirection.DEBIT);
        assertThat(reversed.getProcessedBy()).isEqualTo(88L);
        assertThat(transactions.findById(original.getId()).orElseThrow().getStatus()).isEqualTo(TransactionStatus.REVERSED);
        assertThat(shipper().getAvailableBalance()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> service.reverseTransaction(original.getId(), 88L, null)).isInstanceOf(IllegalStateException.class);
        assertThat(transactions.count()).isEqualTo(4);
    }
    @Test void eligibilityUsesReservationsAndInsufficientCodRollsBackTheInsertedEntry() {
        assertThatThrownBy(() -> service.requestWithdrawal(22L, EntityType.SHIPPER, amount("1"), 22L))
                .isInstanceOf(ResourceNotFoundException.class);
        service.topUpDeposit(22L, EntityType.SHIPPER, amount("100"), null);
        var balance = shipper();
        balance.setReservedDepositBalance(amount("30"));
        balances.saveAndFlush(balance);
        assertThat(service.checkCodEligibility(22L, amount("70"))).isTrue();
        assertThat(service.checkCodEligibility(22L, amount("71"))).isFalse();
        assertThatThrownBy(() -> service.createTransaction(22L, EntityType.SHIPPER, null, TransactionDirection.DEBIT,
                TransactionReason.COD_SETTLEMENT, amount("101"), "cash"))
                .isInstanceOf(InsufficientBalanceException.class).hasMessageContaining("Insufficient COD deposit");
        assertThat(transactions.count()).isEqualTo(1);
        assertThat(shipper().getDepositBalance()).isEqualByComparingTo("100");
        assertThat(shipper().getTotalCodCollected()).isEqualByComparingTo("0");
        service.createTransaction(22L, EntityType.SHIPPER, null, TransactionDirection.DEBIT,
                TransactionReason.COD_SETTLEMENT, amount("100"), "cash");
        assertThat(shipper().getDepositBalance()).isEqualByComparingTo("0");
        assertThat(shipper().getTotalCodCollected()).isEqualByComparingTo("100");
        assertThatThrownBy(() -> service.topUpDeposit(22L, EntityType.SHIPPER, BigDecimal.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(transactions.count()).isEqualTo(2);
    }
}
