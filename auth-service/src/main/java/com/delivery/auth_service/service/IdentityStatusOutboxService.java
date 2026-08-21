package com.delivery.auth_service.service;

import com.delivery.auth_service.entity.AuthAccount;
import com.delivery.auth_service.entity.IdentityOutboxEvent;
import com.delivery.auth_service.repository.IdentityOutboxEventRepository;
import com.delivery.identity.contracts.IdentityStatusChanged;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

@Service
public class IdentityStatusOutboxService {
    private final IdentityOutboxEventRepository events;
    private final ObjectMapper mapper;
    private final boolean enabled;

    public IdentityStatusOutboxService(IdentityOutboxEventRepository events, ObjectMapper mapper,
            @Value("${app.identity.events.enabled:false}") boolean enabled) {
        this.events = events; this.mapper = mapper; this.enabled = enabled;
    }

    public void statusChanged(AuthAccount account, Long changedByPrincipalId, String reasonCode) {
        if (!enabled) return;
        UUID eventId = UUID.randomUUID();
        IdentityStatusChanged payload = new IdentityStatusChanged(eventId, IdentityStatusChanged.TYPE, 1,
                Instant.now(), eventId, null, account.getId(), account.getLifecycleStatus(),
                account.getLifecycleVersion(), reasonCode, changedByPrincipalId);
        IdentityOutboxEvent event = new IdentityOutboxEvent();
        event.setEventId(eventId); event.setEventType(IdentityStatusChanged.TYPE);
        event.setAggregateId(account.getId()); event.setTopic(IdentityStatusChanged.TYPE);
        event.setEventKey(account.getId().toString()); event.setPayload(json(payload));
        events.save(event);
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot serialize identity event", e); }
    }
}
