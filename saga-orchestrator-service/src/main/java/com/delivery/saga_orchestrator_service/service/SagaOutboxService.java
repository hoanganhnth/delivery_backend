package com.delivery.saga_orchestrator_service.service;

import com.delivery.saga_orchestrator_service.entity.SagaOutboxEvent;
import com.delivery.saga_orchestrator_service.repository.SagaOutboxEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SagaOutboxService {
    private final SagaOutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public UUID saveCommand(String orderId, String topic, String key, Object payload) {
        UUID eventId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        JsonNode tree = objectMapper.valueToTree(payload);
        if (!(tree instanceof ObjectNode command)) {
            throw new IllegalArgumentException("Saga command payload must serialize to a JSON object");
        }
        command.put("eventId", eventId.toString());
        if (!command.hasNonNull("eventType")) command.put("eventType", topic);
        if (!command.hasNonNull("occurredAt")) command.put("occurredAt", now.toString());

        SagaOutboxEvent event = new SagaOutboxEvent();
        event.setEventId(eventId);
        event.setAggregateId(requireText(orderId, "orderId"));
        event.setEventType(requireText(topic, "topic"));
        event.setTopic(topic);
        event.setEventKey(requireText(key, "key"));
        event.setPayload(command.toString());
        event.setStatus(SagaOutboxEvent.Status.PENDING);
        event.setAttempts(0);
        event.setNextAttemptAt(now);
        event.setCreatedAt(now);
        repository.save(event);
        return eventId;
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }
}
