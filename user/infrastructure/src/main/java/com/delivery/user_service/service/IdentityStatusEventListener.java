package com.delivery.user_service.service;

import com.delivery.identity.contracts.IdentityStatusChanged;
import com.delivery.user_service.entity.IdentityInboxReceipt;
import com.delivery.user_service.repository.IdentityInboxReceiptRepository;
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

/** User is a versioned status projection; Auth remains the lifecycle authority. */
@Component
@ConditionalOnProperty(name = "app.identity.events.enabled", havingValue = "true")
public class IdentityStatusEventListener {
    private final ObjectMapper mapper; private final com.delivery.user.application.api.UserIdentityStatusUseCase statuses; private final IdentityInboxReceiptRepository receipts;
    public IdentityStatusEventListener(ObjectMapper mapper, com.delivery.user.application.api.UserIdentityStatusUseCase statuses, IdentityInboxReceiptRepository receipts) {
        this.mapper = mapper; this.statuses = statuses; this.receipts = receipts;
    }
    @RetryableTopic(
            attempts = "${app.identity.kafka.retry.attempts:4}",
            backoff = @Backoff(delayExpression = "${app.identity.kafka.retry.initial-delay-ms:1000}",
                    multiplierExpression = "${app.identity.kafka.retry.multiplier:2.0}",
                    maxDelayExpression = "${app.identity.kafka.retry.max-delay-ms:10000}"),
            exclude = IllegalArgumentException.class,
            autoCreateTopics = "${app.identity.kafka.retry.auto-create-topics:false}",
            retryTopicSuffix = "-retry-user-identity",
            dltTopicSuffix = ".user-identity.DLT")
    @KafkaListener(topics = "${app.identity.topics.status-changed:identity.status.changed}",
            groupId = "${app.identity.status-consumer-group:user-identity-status-v1}")
    @Transactional
    public void statusChanged(String raw) throws Exception {
        IdentityStatusChanged event = mapper.readValue(raw, IdentityStatusChanged.class);
        if (!IdentityStatusChanged.TYPE.equals(event.eventType()) || event.principalId() == null
                || event.status() == null || event.lifecycleVersion() < 1) {
            throw new IllegalArgumentException("Invalid identity.status.changed event");
        }
        String fingerprint = fingerprint(raw);
        IdentityInboxReceipt receipt = receipts.findById(event.eventId()).orElse(null);
        if (receipt != null) {
            if (!receipt.getEventType().equals(event.eventType()) || !receipt.getPrincipalId().equals(event.principalId())
                    || !receipt.getPayloadFingerprint().equals(fingerprint)) throw new IllegalStateException("Conflicting identity event reuse");
            return;
        }
        statuses.apply(new com.delivery.user.application.api.ApplyUserIdentityStatusCommand(
                event.principalId(), event.status().name(), event.lifecycleVersion(), event.changedByPrincipalId()));
        IdentityInboxReceipt applied = new IdentityInboxReceipt();
        applied.setEventId(event.eventId()); applied.setEventType(event.eventType()); applied.setPrincipalId(event.principalId());
        applied.setPayloadFingerprint(fingerprint); applied.setProcessedAt(LocalDateTime.now()); receipts.save(applied);
    }
    private static String fingerprint(String raw) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
}
