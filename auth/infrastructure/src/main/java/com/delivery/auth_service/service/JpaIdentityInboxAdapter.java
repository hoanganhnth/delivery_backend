package com.delivery.auth_service.service;
import com.delivery.auth.application.api.IdentityInboxPort;
import com.delivery.auth_service.entity.IdentityInboxReceipt;
import com.delivery.auth_service.repository.IdentityInboxReceiptRepository;
import java.util.*;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
@Component
@RequiredArgsConstructor
public class JpaIdentityInboxAdapter implements IdentityInboxPort {
    private final IdentityInboxReceiptRepository receipts;
    public Optional<Receipt> find(UUID id) {
        return receipts.findById(id).map(row -> new Receipt(row.getEventId(),row.getEventType(),row.getPrincipalId(),row.getPayloadFingerprint(),row.getProcessedAt()));
    }
    public void save(Receipt receipt) {
        var row=new IdentityInboxReceipt();row.setEventId(receipt.eventId());row.setEventType(receipt.eventType());
        row.setPrincipalId(receipt.principalId());row.setPayloadFingerprint(receipt.fingerprint());row.setProcessedAt(receipt.processedAt());
        receipts.save(row);
    }
}
