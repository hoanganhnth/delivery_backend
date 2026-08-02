package com.delivery.settlement_service.service;

import com.delivery.settlement_service.entity.RefundCase;
import com.delivery.settlement_service.entity.RefundOutboxEvent;
import com.delivery.settlement_service.repository.RefundOutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class RefundOutboxService {
    private final RefundOutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final String topic;

    public RefundOutboxService(RefundOutboxEventRepository repository,
                               ObjectMapper objectMapper,
                               @Value("${app.kafka.topics.refund-requested:refund.requested}") String topic) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueue(RefundCase refundCase) {
        String eventType = "REFUND_" + refundCase.getStatus().name();
        UUID eventId = UUID.nameUUIDFromBytes(
                (refundCase.getRefundId() + ":" + eventType).getBytes(StandardCharsets.UTF_8));
        if (repository.existsById(eventId)) {
            return eventId;
        }

        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId);
        payload.put("eventType", eventType);
        payload.put("occurredAt", now);
        payload.put("refundId", refundCase.getRefundId());
        payload.put("orderId", refundCase.getOrderId());
        payload.put("amount", refundCase.getRefundAmount());
        payload.put("currency", refundCase.getCurrency());
        payload.put("paymentMethod", refundCase.getPaymentMethod());
        payload.put("trigger", refundCase.getTrigger());
        payload.put("status", refundCase.getStatus());

        RefundOutboxEvent event = new RefundOutboxEvent();
        event.setEventId(eventId);
        event.setAggregateType("REFUND_CASE");
        event.setAggregateId(refundCase.getRefundId().toString());
        event.setEventType(eventType);
        event.setTopic(topic);
        event.setEventKey(refundCase.getOrderId().toString());
        event.setPayload(json(payload));
        event.setStatus(RefundOutboxEvent.Status.PENDING);
        event.setAttempts(0);
        event.setNextAttemptAt(now);
        event.setCreatedAt(now);
        repository.save(event);
        return eventId;
    }

    private String json(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Refund event is not serializable", e);
        }
    }
}
