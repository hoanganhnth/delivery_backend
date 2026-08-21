package com.delivery.shipper_service.service;

import com.delivery.identity.contracts.ShipperIdentityUpserted;
import com.delivery.shipper_service.entity.Shipper;
import com.delivery.shipper_service.repository.ShipperIdentityOutboxEventRepository;
import com.delivery.shipper_service.repository.ShipperRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ShipperIdentityOutboxService {
    private final ShipperIdentityOutboxEventRepository events;
    private final ObjectMapper mapper;
    private final ShipperRepository shippers;

    public ShipperIdentityOutboxService(ShipperIdentityOutboxEventRepository events, ObjectMapper mapper,
            ShipperRepository shippers) {
        this.events = events; this.mapper = mapper; this.shippers = shippers;
    }

    /** Gradually seed pre-existing, already-linked shipper rows into the new topic. */
    public void seedExisting(int batchSize) {
        shippers.findIdentityOutboxMissing(org.springframework.data.domain.PageRequest.of(0, batchSize))
                .forEach(this::upsert);
    }

    public void upsert(Shipper shipper) {
        if (shipper.getId() == null || shipper.getPrincipalId() == null || shipper.getUserId() == null
                || events.existsByEventTypeAndAggregateId(ShipperIdentityUpserted.TYPE, shipper.getId())) return;
        UUID eventId = UUID.randomUUID();
        ShipperIdentityUpserted payload = new ShipperIdentityUpserted(eventId, ShipperIdentityUpserted.TYPE, 1,
                Instant.now(), eventId, null, shipper.getPrincipalId(), shipper.getUserId(), shipper.getId(), 1L);
        events.insertIfAbsent(eventId, ShipperIdentityUpserted.TYPE, shipper.getId(),
                ShipperIdentityUpserted.TYPE, shipper.getPrincipalId().toString(), json(payload));
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException failure) { throw new IllegalStateException("Cannot serialize shipper identity event", failure); }
    }
}
