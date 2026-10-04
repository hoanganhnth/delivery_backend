package com.delivery.settlement_service.adapter;

import com.delivery.settlement.application.api.refund.RefundOutboxWritePort;
import com.delivery.settlement.domain.refund.RefundOutboxIntent;
import com.delivery.settlement_service.entity.RefundOutboxEvent;
import com.delivery.settlement_service.repository.RefundOutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.UUID;

public final class JpaRefundOutboxWriter implements RefundOutboxWritePort {
    private final RefundOutboxEventRepository repository;
    private final ObjectMapper mapper;
    private final String topic;

    public JpaRefundOutboxWriter(RefundOutboxEventRepository repository, ObjectMapper mapper, String topic) {
        this.repository = repository;
        this.mapper = mapper;
        this.topic = topic;
    }
    @Override public boolean exists(UUID id) { return repository.existsById(id); }
    @Override public void save(RefundOutboxIntent intent) {
        var request = intent.request();
        var payload = new LinkedHashMap<String,Object>();
        payload.put("eventId", intent.eventId());
        payload.put("eventType", intent.eventType());
        payload.put("occurredAt", intent.occurredAt());
        payload.put("refundId", request.refundId());
        payload.put("orderId", request.orderId());
        payload.put("amount", request.amount());
        payload.put("currency", request.currency());
        payload.put("paymentMethod", request.paymentMethod());
        payload.put("trigger", request.trigger());
        payload.put("status", request.status());
        var row = new RefundOutboxEvent();
        row.setEventId(intent.eventId());
        row.setAggregateType(intent.aggregateType());
        row.setAggregateId(intent.aggregateId());
        row.setEventType(intent.eventType());
        row.setTopic(topic);
        row.setEventKey(intent.eventKey());
        try { row.setPayload(mapper.writeValueAsString(payload)); }
        catch (JsonProcessingException failure) { throw new IllegalArgumentException("Refund event is not serializable", failure); }
        row.setStatus(RefundOutboxEvent.Status.PENDING);
        row.setAttempts(0);
        row.setNextAttemptAt(intent.occurredAt());
        row.setCreatedAt(intent.occurredAt());
        repository.save(row);
    }
}
