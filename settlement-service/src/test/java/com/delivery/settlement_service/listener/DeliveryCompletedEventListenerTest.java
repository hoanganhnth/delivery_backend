package com.delivery.settlement_service.listener;

import com.delivery.settlement_service.dto.event.DeliveryCompletedEvent;
import com.delivery.settlement_service.entity.EntityType;
import com.delivery.settlement_service.entity.SettlementReceipt;
import com.delivery.settlement_service.entity.Transaction.TransactionDirection;
import com.delivery.settlement_service.entity.Transaction.TransactionReason;
import com.delivery.settlement_service.entity.Transaction.WalletType;
import com.delivery.settlement_service.repository.TransactionRepository;
import com.delivery.settlement_service.repository.SettlementReceiptRepository;
import com.delivery.settlement_service.service.TransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class DeliveryCompletedEventListenerTest {

    @Mock
    private TransactionService transactionService;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private SettlementReceiptRepository settlementReceiptRepository;

    @Mock
    private Acknowledgment acknowledgment;

    private DeliveryCompletedEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new DeliveryCompletedEventListener(
                transactionService, transactionRepository, settlementReceiptRepository);
    }

    @Test
    void codDeliveryPostsBalancedEntriesExactlyOnceAtCompletion() throws Exception {
        DeliveryCompletedEvent event = validEvent("COD");
        when(transactionRepository.existsByOrderIdAndEntityIdAndEntityTypeAndReason(
                101L, 0L, EntityType.SYSTEM, TransactionReason.PLATFORM_COMMISSION)).thenReturn(false);

        listener.handleDeliveryCompleted(
                new ObjectMapper().findAndRegisterModules().writeValueAsString(event),
                "delivery.completed", 0, System.currentTimeMillis(), acknowledgment);

        verify(transactionService).createTransaction(
                11L, EntityType.RESTAURANT, 101L, TransactionDirection.CREDIT,
                TransactionReason.ORDER_EARNING, new BigDecimal("80000"),
                "Doanh thu đơn #101 (đã trừ hoa hồng)", WalletType.EARNINGS);
        verify(transactionService).createTransaction(
                22L, EntityType.SHIPPER, 101L, TransactionDirection.CREDIT,
                TransactionReason.DELIVERY_FEE, new BigDecimal("17000"),
                "Tiền công giao đơn #101", WalletType.EARNINGS);
        verify(transactionService).createTransaction(
                eq(22L), eq(EntityType.SHIPPER), eq(101L), eq(TransactionDirection.DEBIT),
                eq(TransactionReason.COD_SETTLEMENT), eq(new BigDecimal("120000")),
                contains("Đối trừ COD"), eq(WalletType.DEPOSIT));
        verify(transactionService).createTransaction(
                0L, EntityType.SYSTEM, 101L, TransactionDirection.CREDIT,
                TransactionReason.PLATFORM_COMMISSION, new BigDecimal("23000"),
                "Hoa hồng nền tảng đơn #101", WalletType.EARNINGS);
        verify(transactionService, times(4)).createTransaction(
                anyLong(), any(), anyLong(), any(), any(), any(), anyString(), any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void exactEventReplayDoesNotPostAgain() throws Exception {
        DeliveryCompletedEvent event = validEvent("COD");

        listener.handleDeliveryCompleted(
                new ObjectMapper().findAndRegisterModules().writeValueAsString(event),
                "delivery.completed", 0, System.currentTimeMillis(), acknowledgment);

        var receiptCaptor = org.mockito.ArgumentCaptor.forClass(SettlementReceipt.class);
        verify(settlementReceiptRepository).saveAndFlush(receiptCaptor.capture());
        when(settlementReceiptRepository.findById(event.getEventId()))
                .thenReturn(Optional.of(receiptCaptor.getValue()));

        listener.handleDeliveryCompleted(
                new ObjectMapper().findAndRegisterModules().writeValueAsString(event),
                "delivery.completed", 0, System.currentTimeMillis(), acknowledgment);

        verify(transactionService, times(4)).createTransaction(
                anyLong(), any(), anyLong(), any(), any(), any(), anyString(), any());
        verify(acknowledgment, times(2)).acknowledge();
    }

    @Test
    void sameEventIdWithDifferentPayloadIsRejected() throws Exception {
        DeliveryCompletedEvent event = validEvent("COD");
        listener.handleDeliveryCompleted(
                new ObjectMapper().findAndRegisterModules().writeValueAsString(event),
                "delivery.completed", 0, 1L, acknowledgment);

        var receiptCaptor = org.mockito.ArgumentCaptor.forClass(SettlementReceipt.class);
        verify(settlementReceiptRepository).saveAndFlush(receiptCaptor.capture());
        when(settlementReceiptRepository.findById(event.getEventId()))
                .thenReturn(Optional.of(receiptCaptor.getValue()));
        event.setRestaurantEarnings(new BigDecimal("70000"));

        assertThrows(IllegalArgumentException.class, () -> listener.handleDeliveryCompleted(
                new ObjectMapper().findAndRegisterModules().writeValueAsString(event),
                "delivery.completed", 0, 2L, acknowledgment));
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    void differentEventForSettledOrderIsRejected() throws Exception {
        DeliveryCompletedEvent event = validEvent("COD");
        SettlementReceipt existing = SettlementReceipt.builder()
                .eventId(UUID.randomUUID()).orderId(101L).deliveryId(1L)
                .payloadFingerprint("existing").build();
        when(settlementReceiptRepository.findByOrderId(101L)).thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class, () -> listener.handleDeliveryCompleted(
                new ObjectMapper().findAndRegisterModules().writeValueAsString(event),
                "delivery.completed", 0, 1L, acknowledgment));

        verifyNoInteractions(transactionService);
        verifyNoInteractions(acknowledgment);
    }

    @Test
    void nonCodOrInconsistentCommissionIsSentToRetryAndDltBeforeLedgerAccess() throws Exception {
        DeliveryCompletedEvent online = validEvent("ONLINE");
        DeliveryCompletedEvent inconsistent = validEvent("COD");
        inconsistent.setTotalPlatformEarnings(new BigDecimal("999"));

        assertThrows(IllegalArgumentException.class, () -> listener.handleDeliveryCompleted(
                new ObjectMapper().findAndRegisterModules().writeValueAsString(online),
                "delivery.completed", 0, 1L, acknowledgment));
        assertThrows(IllegalArgumentException.class, () -> listener.handleDeliveryCompleted(
                new ObjectMapper().findAndRegisterModules().writeValueAsString(inconsistent),
                "delivery.completed", 0, 2L, acknowledgment));

        verifyNoInteractions(transactionService, transactionRepository);
        verifyNoInteractions(acknowledgment);
    }

    @Test
    void inconsistentShippingSplitIsRejectedBeforeReceiptOrLedgerAccess() throws Exception {
        DeliveryCompletedEvent inconsistent = validEvent("COD");
        inconsistent.setShipperEarnings(new BigDecimal("16000"));

        assertThrows(IllegalArgumentException.class, () -> listener.handleDeliveryCompleted(
                new ObjectMapper().findAndRegisterModules().writeValueAsString(inconsistent),
                "delivery.completed", 0, 1L, acknowledgment));

        verifyNoInteractions(transactionService, transactionRepository, settlementReceiptRepository);
        verifyNoInteractions(acknowledgment);
    }

    private DeliveryCompletedEvent validEvent(String paymentMethod) {
        return DeliveryCompletedEvent.builder()
                .eventId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .eventType("DELIVERY_COMPLETED")
                .deliveryId(1L)
                .orderId(101L)
                .restaurantId(11L)
                .shipperId(22L)
                .restaurantEarnings(new BigDecimal("80000"))
                .restaurantCommission(new BigDecimal("20000"))
                .shippingFee(new BigDecimal("20000"))
                .shipperEarnings(new BigDecimal("17000"))
                .shippingCommission(new BigDecimal("3000"))
                .totalPlatformEarnings(new BigDecimal("23000"))
                .paymentMethod(paymentMethod)
                .build();
    }
}
