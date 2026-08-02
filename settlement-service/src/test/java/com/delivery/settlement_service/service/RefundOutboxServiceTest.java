package com.delivery.settlement_service.service;

import com.delivery.settlement_service.entity.RefundCase;
import com.delivery.settlement_service.entity.RefundOutboxEvent;
import com.delivery.settlement_service.repository.RefundOutboxEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundOutboxServiceTest {
    @Mock RefundOutboxEventRepository repository;

    @Test
    void enqueueUsesDeterministicEventIdentityAndPersistsRefundSnapshot() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        RefundOutboxService service = new RefundOutboxService(repository, objectMapper, "refund.requested");
        RefundCase refundCase = refundCase();
        UUID expectedEventId = UUID.nameUUIDFromBytes(
                (refundCase.getRefundId() + ":REFUND_REQUESTED").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(repository.existsById(expectedEventId)).thenReturn(false, true);

        UUID first = service.enqueue(refundCase);
        UUID second = service.enqueue(refundCase);

        assertThat(second).isEqualTo(first);
        ArgumentCaptor<RefundOutboxEvent> captured = ArgumentCaptor.forClass(RefundOutboxEvent.class);
        verify(repository, org.mockito.Mockito.times(2)).existsById(first);
        verify(repository).save(captured.capture());
        JsonNode payload = objectMapper.readTree(captured.getValue().getPayload());
        assertThat(captured.getValue().getEventId()).isEqualTo(first);
        assertThat(captured.getValue().getTopic()).isEqualTo("refund.requested");
        assertThat(payload.get("refundId").asText()).isEqualTo(refundCase.getRefundId().toString());
        assertThat(payload.get("amount").decimalValue()).isEqualByComparingTo("120000");
    }

    @Test
    void existingOutboxIdentityIsNotInsertedAgain() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        RefundOutboxService service = new RefundOutboxService(repository, objectMapper, "refund.requested");
        RefundCase refundCase = refundCase();
        when(repository.existsById(any())).thenReturn(true);

        UUID eventId = service.enqueue(refundCase);

        assertThat(eventId).isNotNull();
        verify(repository, never()).save(any());
    }

    private RefundCase refundCase() {
        return RefundCase.builder()
                .refundId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
                .orderId(101L)
                .refundAmount(new BigDecimal("120000"))
                .currency("VND")
                .paymentMethod("ONLINE")
                .trigger(RefundCase.RefundTrigger.ORDER_CANCELLED)
                .status(RefundCase.RefundStatus.REQUESTED)
                .createdAt(LocalDateTime.of(2026, 8, 2, 10, 0))
                .build();
    }
}
