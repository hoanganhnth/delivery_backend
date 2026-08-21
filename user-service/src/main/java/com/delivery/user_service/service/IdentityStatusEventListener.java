package com.delivery.user_service.service;

import com.delivery.identity.contracts.IdentityLifecycleStatus;
import com.delivery.identity.contracts.IdentityStatusChanged;
import com.delivery.user_service.entity.IdentityInboxReceipt;
import com.delivery.user_service.entity.User;
import com.delivery.user_service.repository.IdentityInboxReceiptRepository;
import com.delivery.user_service.repository.UserRepository;
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
    private final ObjectMapper mapper; private final UserRepository users; private final IdentityInboxReceiptRepository receipts;
    public IdentityStatusEventListener(ObjectMapper mapper, UserRepository users, IdentityInboxReceiptRepository receipts) {
        this.mapper = mapper; this.users = users; this.receipts = receipts;
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
        User user = users.findByPrincipalId(event.principalId()).orElse(null);
        if (user != null) {
            long current = user.getIdentityStatusVersion() == null ? 0L : user.getIdentityStatusVersion();
            // A gap means this projection has missed authoritative history. Do not
            // silently apply a later state: retry/DLT preserves the evidence and
            // lets an operator replay the missing event in order.
            // Version 0 is an uninitialized projection baseline (including
            // pre-migration profiles). Its first event is an authoritative
            // Auth snapshot and may legitimately be > 1 when block/unblock
            // occurred before profile creation. Once a projection has applied
            // any version, a gap remains fail-closed and retryable.
            if (current > 0 && event.lifecycleVersion() > current + 1) {
                throw new IllegalStateException("Identity lifecycle version gap");
            }
            if (event.lifecycleVersion() > current) {
                user.setIdentityStatus(event.status().name());
                user.setIdentityStatusVersion(event.lifecycleVersion());
                boolean blocked = event.status() == IdentityLifecycleStatus.BLOCKED;
                user.setIsBlocked(blocked); user.setIsActive(!blocked);
                if (blocked) { user.setBlockedAt(LocalDateTime.now()); user.setBlockedBy(event.changedByPrincipalId()); }
                else { user.setBlockedAt(null); user.setBlockedBy(null); user.setBlockReason(null); }
                users.save(user);
            }
        }
        IdentityInboxReceipt applied = new IdentityInboxReceipt();
        applied.setEventId(event.eventId()); applied.setEventType(event.eventType()); applied.setPrincipalId(event.principalId());
        applied.setPayloadFingerprint(fingerprint); applied.setProcessedAt(LocalDateTime.now()); receipts.save(applied);
    }
    private static String fingerprint(String raw) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
}
