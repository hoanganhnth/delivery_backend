package com.delivery.settlement_service.service;

import com.delivery.settlement_service.dto.request.CodCapacityHoldRequest;
import com.delivery.settlement_service.entity.*;
import com.delivery.settlement_service.repository.*;
import com.delivery.settlement_service.listener.BatchCodHoldTransitionListener;
import com.delivery.settlement_service.exception.InsufficientBalanceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.kafka.support.Acknowledgment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class CodCapacityHoldIntegrationTest {
    @Autowired CodCapacityHoldService service;
    @Autowired TransactionService ledger;
    @Autowired CodCapacityHoldRepository holds;
    @Autowired BalanceRepository balances;
    @Autowired TransactionRepository transactions;
    @Autowired SettlementReceiptRepository receipts;
    @Autowired BatchCodHoldTransitionListener listener;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @BeforeEach @AfterEach void clear() {
        holds.deleteAll(); receipts.deleteAll(); transactions.deleteAll(); balances.deleteAll();
    }
    private BigDecimal reserved() { return balances.findByEntityIdAndEntityType(22L, EntityType.SHIPPER).orElseThrow().getReservedDepositBalance(); }
    private CodCapacityHoldRequest request(String... amounts) {
        var request = new CodCapacityHoldRequest();
        request.setEventId(UUID.randomUUID()); request.setShipperId(22L); request.setMatchingSessionId(UUID.randomUUID());
        var items = new ArrayList<CodCapacityHoldRequest.Item>();
        for (int i=0; i<amounts.length; i++) {
            var item = new CodCapacityHoldRequest.Item();
            item.setOfferId(UUID.randomUUID()); item.setOrderId((long)i+1); item.setDeliveryId((long)i+10);
            item.setAmount(new BigDecimal(amounts[i])); item.setExpiresAt(LocalDateTime.now().plusMinutes(5)); items.add(item);
        }
        request.setOffers(items); return request;
    }
    private void fund() { ledger.topUpDeposit(22L, EntityType.SHIPPER, new BigDecimal("100"), null); }
    @Test void batchReplayAndConsumeRetainOneReservationPerHoldAndSeparateDepositMoney() {
        fund(); var request = request("30", "40"); var created = service.hold(request);
        assertThat(reserved()).isEqualByComparingTo("70");
        assertThat(service.hold(request)).extracting(CodCapacityHold::getHoldId).containsExactlyElementsOf(created.stream().map(CodCapacityHold::getHoldId).toList());
        assertThat(holds.count()).isEqualTo(2);
        assertThat(ledger.checkCodEligibility(22L, new BigDecimal("31"))).isFalse();
        service.transition(created.get(0).getHoldId(), CodCapacityHoldStatus.COMMITTED);
        service.consumeForDelivery(created.get(0).getDeliveryId());
        assertThat(reserved()).isEqualByComparingTo(created.get(1).getAmount());
        service.consumeForDelivery(created.get(1).getDeliveryId());
        service.consumeForDelivery(created.get(1).getDeliveryId());
        assertThat(reserved()).isEqualByComparingTo("0");
        assertThat(holds.findAll()).allSatisfy(h -> { assertThat(h.getStatus()).isEqualTo(CodCapacityHoldStatus.CONSUMED); assertThat(h.getConsumedAt()).isNotNull(); });
        assertThat(balances.findByEntityIdAndEntityType(22L, EntityType.SHIPPER).orElseThrow().getDepositBalance()).isEqualByComparingTo("100");
    }
    @Test void expiryAndInsufficientCapacityDoNotLeavePartialReservations() {
        fund(); assertThatThrownBy(() -> service.hold(request("60", "50"))).isInstanceOf(InsufficientBalanceException.class);
        assertThat(holds.count()).isZero(); assertThat(reserved()).isEqualByComparingTo("0");
        var due = request("30", "40"); due.getOffers().forEach(i -> i.setExpiresAt(LocalDateTime.now().minusMinutes(1)));
        var created = service.hold(due);
        assertThat(service.transition(created.get(0).getHoldId(), CodCapacityHoldStatus.COMMITTED).getStatus()).isEqualTo(CodCapacityHoldStatus.EXPIRED);
        service.expireDueHolds();
        assertThat(holds.findAll()).allSatisfy(h -> assertThat(h.getStatus()).isEqualTo(CodCapacityHoldStatus.EXPIRED));
        assertThat(reserved()).isEqualByComparingTo("0");
    }
    @Test void failedBatchTransitionRollsBackEarlierItemsAndDoesNotAcknowledge() throws Exception {
        fund(); var hold = service.hold(request("30")).get(0); var acknowledgment = mock(Acknowledgment.class);
        String message = new ObjectMapper().writeValueAsString(Map.of("target", "COMMITTED", "holdIds", List.of(hold.getHoldId(), UUID.randomUUID())));
        assertThatThrownBy(() -> listener.handle(message, acknowledgment)).isInstanceOf(IllegalArgumentException.class);
        assertThat(holds.findById(hold.getHoldId()).orElseThrow().getStatus()).isEqualTo(CodCapacityHoldStatus.HELD);
        assertThat(reserved()).isEqualByComparingTo("30"); verifyNoInteractions(acknowledgment);
    }
    @Test void batchAcknowledgesOnlyAfterTheOuterFinancialTransactionCommits() throws Exception {
        fund(); var hold = service.hold(request("30")).get(0);
        var acknowledged = new java.util.concurrent.atomic.AtomicBoolean();
        Acknowledgment acknowledgment = () -> acknowledged.set(true);
        String message = new ObjectMapper().writeValueAsString(Map.of("target", "COMMITTED", "holdIds", List.of(hold.getHoldId())));
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            listener.handle(message, acknowledgment);
            assertThat(acknowledged.get()).as("offset must not get ahead of the database commit").isFalse();
        });
        assertThat(acknowledged.get()).isTrue();
        assertThat(holds.findById(hold.getHoldId()).orElseThrow().getStatus()).isEqualTo(CodCapacityHoldStatus.COMMITTED);
    }
    @Test void rolledBackOuterBatchTransactionNeverAcknowledges() throws Exception {
        fund(); var hold = service.hold(request("30")).get(0);
        var acknowledged = new java.util.concurrent.atomic.AtomicBoolean();
        String message = new ObjectMapper().writeValueAsString(Map.of("target", "RELEASED", "holdIds", List.of(hold.getHoldId())));
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            listener.handleRelease(message, () -> acknowledged.set(true));
            tx.setRollbackOnly();
        });
        assertThat(acknowledged.get()).isFalse();
        assertThat(holds.findById(hold.getHoldId()).orElseThrow().getStatus()).isEqualTo(CodCapacityHoldStatus.HELD);
        assertThat(reserved()).isEqualByComparingTo("30");
    }

}
