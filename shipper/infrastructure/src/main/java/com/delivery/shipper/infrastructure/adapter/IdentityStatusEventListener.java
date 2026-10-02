package com.delivery.shipper.infrastructure.adapter;

import com.delivery.identity.contracts.IdentityStatusChanged;
import com.delivery.shipper.infrastructure.entity.IdentityInboxReceipt;
import com.delivery.shipper.infrastructure.repository.IdentityInboxReceiptRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Kafka adapter for the authoritative Auth identity status projection. */
@Component
@ConditionalOnProperty(name = "app.identity.events.enabled", havingValue = "true")
public class IdentityStatusEventListener {
    private final ObjectMapper mapper;
    private final com.delivery.shipper.application.api.ShipperUseCases.ProjectIdentityStatus projectIdentityStatus;
    private final IdentityInboxReceiptRepository receipts;

    public IdentityStatusEventListener(ObjectMapper mapper,
            com.delivery.shipper.application.api.ShipperUseCases.ProjectIdentityStatus projectIdentityStatus,
            IdentityInboxReceiptRepository receipts) {
        this.mapper = mapper; this.projectIdentityStatus = projectIdentityStatus; this.receipts = receipts;
    }

    @RetryableTopic(attempts = "${app.identity.kafka.retry.attempts:4}",
            backoff = @Backoff(delayExpression = "${app.identity.kafka.retry.initial-delay-ms:1000}",
                    multiplierExpression = "${app.identity.kafka.retry.multiplier:2.0}",
                    maxDelayExpression = "${app.identity.kafka.retry.max-delay-ms:10000}"),
            exclude = IllegalArgumentException.class,
            autoCreateTopics = "${app.identity.kafka.retry.auto-create-topics:false}",
            retryTopicSuffix = "-retry-shipper-identity", dltTopicSuffix = ".shipper-identity.DLT")
    @KafkaListener(topics = "${app.identity.topics.status-changed:identity.status.changed}",
            groupId = "${app.identity.status-consumer-group:shipper-identity-status-v1}")
    @Transactional
    public void statusChanged(String raw) throws Exception {
        IdentityStatusChanged event = mapper.readValue(raw, IdentityStatusChanged.class);
        if (!IdentityStatusChanged.TYPE.equals(event.eventType()) || event.principalId() == null
                || event.status() == null || event.lifecycleVersion() < 1)
            throw new IllegalArgumentException("Invalid identity.status.changed event");
        String fingerprint = fingerprint(raw);
        IdentityInboxReceipt previous = receipts.findById(event.eventId()).orElse(null);
        if (previous != null) {
            if (!previous.getEventType().equals(event.eventType())
                    || !previous.getPrincipalId().equals(event.principalId())
                    || !previous.getPayloadFingerprint().equals(fingerprint))
                throw new IllegalStateException("Conflicting identity event reuse");
            return;
        }
        projectIdentityStatus.execute(new com.delivery.shipper.application.api.ShipperCommands.IdentityStatusProjection(
                event.principalId(), event.status().name(), event.lifecycleVersion()));
        IdentityInboxReceipt receipt = new IdentityInboxReceipt();
        receipt.setEventId(event.eventId()); receipt.setEventType(event.eventType());
        receipt.setPrincipalId(event.principalId()); receipt.setPayloadFingerprint(fingerprint);
        receipt.setProcessedAt(LocalDateTime.now()); receipts.save(receipt);
    }

    private static String fingerprint(String raw) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }
}
