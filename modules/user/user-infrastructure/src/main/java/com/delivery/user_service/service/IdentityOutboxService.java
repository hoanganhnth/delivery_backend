package com.delivery.user_service.service;

import com.delivery.identity.contracts.IdentityProfileCreated;
import com.delivery.user_service.entity.IdentityOutboxEvent;
import com.delivery.user_service.repository.IdentityOutboxEventRepository;
import com.delivery.user_service.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class IdentityOutboxService {
    private final IdentityOutboxEventRepository events;
    private final ObjectMapper objectMapper;
    private final UserRepository users;

    public IdentityOutboxService(IdentityOutboxEventRepository events, ObjectMapper objectMapper, UserRepository users) {
        this.events = events; this.objectMapper = objectMapper; this.users = users;
    }

    public void profileCreated(Long principalId, Long profileId) {
        UUID eventId = UUID.randomUUID();
        IdentityProfileCreated payload = new IdentityProfileCreated(eventId, IdentityProfileCreated.TYPE, 1,
                Instant.now(), eventId, null, principalId, "USER_PROFILE", profileId, 1L);
        events.insertProfileCreatedIfAbsent(eventId, IdentityProfileCreated.TYPE, principalId,
                IdentityProfileCreated.TYPE, principalId.toString(), json(payload));
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot serialize identity event", e); }
    }
}
