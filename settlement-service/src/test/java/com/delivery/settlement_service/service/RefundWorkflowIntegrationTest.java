package com.delivery.settlement_service.service;

import com.delivery.settlement_service.dto.event.OrderCancelledEvent;
import com.delivery.settlement_service.repository.RefundCaseRepository;
import com.delivery.settlement_service.repository.RefundOutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RefundWorkflowIntegrationTest {
    @Autowired RefundCaseRepository cases;
    @Autowired RefundOutboxEventRepository events;
    @Autowired RefundOutboxService outbox;
    @Autowired ObjectMapper mapper;
    @Autowired Environment environment;
    @Autowired PlatformTransactionManager transactions;
    private RefundCaseService enabledFixture;
    @BeforeEach void prepare() {
        clear();
        enabledFixture=new RefundCaseService(cases,outbox,mapper,true);
        ReflectionTestUtils.setField(enabledFixture,"dataSourceUrl",environment.getProperty("spring.datasource.url"));
    }
    @AfterEach void clear() {events.deleteAll();cases.deleteAll();}
    private OrderCancelledEvent event() {
        return OrderCancelledEvent.builder().eventId(UUID.randomUUID()).eventType("ORDER_CANCELLED")
                .orderId(123L).userId(22L).userPrincipalId(222L).restaurantId(33L)
                .previousStatus("PENDING").currentStatus("CANCELLED").cancelReason("cancelled")
                .cancelledBySource("SYSTEM").cancelReasonCode("SYSTEM_CANCELLED").paymentMethod("ONLINE")
                .subtotalPrice(BigDecimal.TEN).discountAmount(BigDecimal.ZERO).shippingFee(BigDecimal.ONE)
                .totalPrice(new BigDecimal("11")).build();
    }
    @Test void exactReplayPersistsOneReceiptAndOneOutboxWithCanonicalMoney() throws Exception {
        var event=event();var tx=new TransactionTemplate(transactions);
        var first=tx.execute(status->enabledFixture.processOrderCancellation(event));
        var replay=tx.execute(status->enabledFixture.processOrderCancellation(event));
        assertThat(replay.getRefundId()).isEqualTo(first.getRefundId());
        assertThat(cases.count()).isEqualTo(1);assertThat(events.count()).isEqualTo(1);
        var stored=events.findAll().get(0);var payload=mapper.readTree(stored.getPayload());
        assertThat(payload.path("refundId").asText()).isEqualTo(first.getRefundId().toString());
        assertThat(payload.path("amount").decimalValue()).isEqualByComparingTo("11");
        assertThat(payload.path("status").asText()).isEqualTo("REQUESTED");
        assertThat(cases.findById(first.getRefundId()).orElseThrow().getUserPrincipalId()).isEqualTo(222L);
        event.setCancelReason("contradictory");
        assertThatThrownBy(()->tx.execute(status->enabledFixture.processOrderCancellation(event)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("contradictory");
        assertThat(cases.count()).isEqualTo(1);assertThat(events.count()).isEqualTo(1);
    }
    @Test void failureAfterOutboxInsertionRollsBackReceiptAndOutboxTogether() {
        assertThatThrownBy(()->new TransactionTemplate(transactions).executeWithoutResult(status->{
            enabledFixture.processOrderCancellation(event());
            assertThat(cases.count()).isEqualTo(1);assertThat(events.count()).isEqualTo(1);
            throw new IllegalStateException("injected after outbox insertion");
        })).isInstanceOf(IllegalStateException.class).hasMessage("injected after outbox insertion");
        assertThat(cases.count()).isZero();assertThat(events.count()).isZero();
    }
}
