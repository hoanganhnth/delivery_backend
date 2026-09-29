package com.delivery.shipper.infrastructure.adapter;

import com.delivery.shipper.application.api.ShipperCommands;
import com.delivery.shipper.application.api.ShipperPorts;
import com.delivery.shipper.infrastructure.entity.IdentityInboxReceipt;
import com.delivery.shipper.infrastructure.repository.IdentityInboxReceiptRepository;
import org.springframework.stereotype.Component;

@Component
public class JpaIdentityReceiptAdapter implements ShipperPorts.IdentityReceiptStore {
    
    private final IdentityInboxReceiptRepository repository;

    public JpaIdentityReceiptAdapter(IdentityInboxReceiptRepository repository) {
        this.repository = repository;
    }

    private java.util.UUID getEventId(ShipperCommands.IdentityStatusProjection command) {
        String key = String.format("%d:%d", command.principalId(), command.version());
        return java.util.UUID.nameUUIDFromBytes(key.getBytes());
    }

    @Override
    public boolean alreadyProcessed(ShipperCommands.IdentityStatusProjection command) {
        return repository.existsById(getEventId(command));
    }

    @Override
    public void record(ShipperCommands.IdentityStatusProjection command) {
        java.util.UUID eventId = getEventId(command);
        if (repository.existsById(eventId)) {
            return;
        }
        var receipt = new IdentityInboxReceipt();
        receipt.setEventId(eventId);
        receipt.setEventType("IdentityStatusProjection");
        receipt.setPrincipalId(command.principalId());
        receipt.setPayloadFingerprint(command.status());
        receipt.setProcessedAt(java.time.LocalDateTime.now());
        repository.save(receipt);
    }
}
