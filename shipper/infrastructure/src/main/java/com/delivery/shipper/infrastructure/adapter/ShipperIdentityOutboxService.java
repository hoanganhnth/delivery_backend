package com.delivery.shipper.infrastructure.adapter;

import com.delivery.identity.contracts.ShipperIdentityUpserted;
import com.delivery.shipper.domain.outbox.IdentityUpserted;
import com.delivery.shipper.infrastructure.entity.Shipper;
import com.delivery.shipper.infrastructure.repository.ShipperIdentityOutboxEventRepository;
import com.delivery.shipper.infrastructure.repository.ShipperRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** Persistence adapter for the transactional shipper identity outbox. */
@Component
public final class ShipperIdentityOutboxService implements com.delivery.shipper.application.api.ShipperPorts.IdentityOutbox {
    private final ShipperIdentityOutboxEventRepository events;
    private final ObjectMapper mapper;
    private final ShipperRepository shippers;

    public ShipperIdentityOutboxService(ShipperIdentityOutboxEventRepository events, ObjectMapper mapper,
            ShipperRepository shippers) {
        this.events = events; this.mapper = mapper; this.shippers = shippers;
    }

    public void seedExisting(int batchSize) {
        shippers.findIdentityOutboxMissing(PageRequest.of(0, batchSize)).forEach(this::upsert);
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

    @Override
    public boolean enqueueIfNew(IdentityUpserted event) {
        if (event == null) return false;
        String payload = json(new ShipperIdentityUpserted(event.eventId(), ShipperIdentityUpserted.TYPE, 1,
                event.occurredAt(), event.eventId(), null, event.principalId(), event.legacyUserId(),
                event.shipperId(), event.mappingVersion()));
        return events.insertIfAbsent(event.eventId(), ShipperIdentityUpserted.TYPE, event.shipperId(),
                ShipperIdentityUpserted.TYPE, Long.toString(event.principalId()), payload) > 0;
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException failure) { throw new IllegalStateException("Cannot serialize shipper identity event", failure); }
    }
}
