package com.delivery.settlement_service.service;

import com.delivery.settlement_service.entity.EntityType;
import com.delivery.settlement_service.entity.Transaction.TransactionReason;
import com.delivery.settlement_service.entity.Transaction.TransactionStatus;
import com.delivery.settlement_service.mapper.BalanceMapper;
import com.delivery.settlement_service.mapper.TransactionMapper;
import com.delivery.settlement_service.repository.BalanceRepository;
import com.delivery.settlement_service.repository.TransactionRepository;
import com.delivery.settlement_service.service.impl.BalanceServiceImpl;
import com.delivery.settlement_service.service.impl.TransactionServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettlementAdminListBoundsTest {

    @Test
    void transactionCompatibilityQueriesAreBounded() {
        TransactionRepository transactions = mock(TransactionRepository.class);
        when(transactions.findAllByOrderByCreatedAtDesc(any(Pageable.class))).thenReturn(List.of());
        when(transactions.findByStatusAndReasonOrderByCreatedAtDesc(
                eq(TransactionStatus.PENDING), eq(TransactionReason.WITHDRAW), any(Pageable.class)))
                .thenReturn(List.of());
        when(transactions.findByEntityIdAndEntityTypeOrderByCreatedAtDesc(
                eq(7L), eq(EntityType.SHIPPER), any(Pageable.class))).thenReturn(List.of());
        TransactionServiceImpl service = new TransactionServiceImpl(
                transactions, mock(BalanceRepository.class), mock(BalanceService.class), new TransactionMapper());

        assertThat(service.getAllTransactions()).isEmpty();
        assertThat(service.getPendingWithdrawals()).isEmpty();
        assertThat(service.getTransactions(7L, EntityType.SHIPPER)).isEmpty();

        ArgumentCaptor<Pageable> allPage = ArgumentCaptor.forClass(Pageable.class);
        verify(transactions).findAllByOrderByCreatedAtDesc(allPage.capture());
        assertThat(allPage.getValue().getPageSize()).isEqualTo(100);
        verify(transactions).findByStatusAndReasonOrderByCreatedAtDesc(
                eq(TransactionStatus.PENDING), eq(TransactionReason.WITHDRAW), any(Pageable.class));
        verify(transactions).findByEntityIdAndEntityTypeOrderByCreatedAtDesc(
                eq(7L), eq(EntityType.SHIPPER), any(Pageable.class));
    }

    @Test
    void balanceCompatibilityQueryIsBounded() {
        BalanceRepository balances = mock(BalanceRepository.class);
        when(balances.findAll(any(Pageable.class))).thenReturn(Page.empty());
        BalanceServiceImpl service = new BalanceServiceImpl(
                balances, mock(TransactionRepository.class), new BalanceMapper());

        assertThat(service.getAllBalances()).isEmpty();

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(balances).findAll(page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(100);
    }
}
