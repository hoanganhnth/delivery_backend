package com.delivery.auth_service.service;

import com.delivery.auth_service.entity.AuthAccount;
import com.delivery.auth_service.entity.IdentityInboxReceipt;
import com.delivery.auth_service.repository.AuthAccountRepository;
import com.delivery.auth_service.repository.IdentityInboxReceiptRepository;
import com.delivery.identity.contracts.IdentityLifecycleStatus;
import com.delivery.identity.contracts.IdentityProfileCreated;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

/** Links the legacy profile ID from the User outbox without an Auth/User RPC. */
@Component
@ConditionalOnProperty(name = "app.identity.events.enabled", havingValue = "true")
public class IdentityProfileEventListener {
    private final ObjectMapper mapper;
    private final AuthAccountRepository accounts;
    private final IdentityInboxReceiptRepository receipts;
    private final IdentityStatusOutboxService statusOutbox;

    public IdentityProfileEventListener(ObjectMapper mapper, AuthAccountRepository accounts,
            IdentityInboxReceiptRepository receipts, IdentityStatusOutboxService statusOutbox) {
        this.mapper = mapper; this.accounts = accounts; this.receipts = receipts; this.statusOutbox = statusOutbox;
    }

    @RetryableTopic(
            attempts = "${app.identity.kafka.retry.attempts:4}",
            backoff = @Backoff(delayExpression = "${app.identity.kafka.retry.initial-delay-ms:1000}",
                    multiplierExpression = "${app.identity.kafka.retry.multiplier:2.0}",
                    maxDelayExpression = "${app.identity.kafka.retry.max-delay-ms:10000}"),
            exclude = IllegalArgumentException.class,
            autoCreateTopics = "${app.identity.kafka.retry.auto-create-topics:false}",
            retryTopicSuffix = "-retry-auth-identity",
            dltTopicSuffix = ".auth-identity.DLT")
    @KafkaListener(topics = "${app.identity.topics.profile-created:identity.profile.created}",
            groupId = "${app.identity.profile-consumer-group:auth-identity-profile-v1}")
    @Transactional
    public void profileCreated(String raw) throws Exception {
        IdentityProfileCreated event = mapper.readValue(raw, IdentityProfileCreated.class);
        if (!IdentityProfileCreated.TYPE.equals(event.eventType()) || event.principalId() == null
                || event.profileId() == null || event.profileId() <= 0
                || !"USER_PROFILE".equals(event.profileType())) {
            throw new IllegalArgumentException("Invalid identity.profile.created event");
        }
        String fingerprint = fingerprint(raw);
        IdentityInboxReceipt receipt = receipts.findById(event.eventId()).orElse(null);
        if (receipt != null) {
            if (!receipt.getEventType().equals(event.eventType())
                    || !receipt.getPrincipalId().equals(event.principalId())
                    || !receipt.getPayloadFingerprint().equals(fingerprint)) {
                throw new IllegalStateException("Conflicting identity event reuse");
            }
            return;
        }
        AuthAccount account = accounts.findByIdForUpdate(event.principalId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown principal in profile event"));
        if (account.getUserId() != null && !account.getUserId().equals(event.profileId())) {
            throw new IllegalStateException("Principal already linked to a different user profile");
        }
        account.setUserId(event.profileId());
        var before = account.getLifecycleStatus();
        if (!Boolean.TRUE.equals(account.getIsActive())) {
            transition(account, IdentityLifecycleStatus.BLOCKED);
        } else if (Boolean.TRUE.equals(account.getEmailVerificationRequired())
                && account.getEmailVerifiedAt() == null) {
            transition(account, IdentityLifecycleStatus.PENDING_EMAIL_VERIFICATION);
        } else {
            transition(account, IdentityLifecycleStatus.ACTIVE);
        }
        accounts.save(account);
        // The initial status event can arrive before User has created its
        // profile (for example, an admin blocks PENDING_PROFILE). User rightly
        // ACKs that event because no projection exists yet. When the profile
        // is linked, emit the authoritative snapshot again for BLOCKED so the
        // newly-created projection cannot remain incorrectly active.
        if (before != account.getLifecycleStatus()
                || account.getLifecycleStatus() == IdentityLifecycleStatus.BLOCKED) {
            statusOutbox.statusChanged(account, null, "PROFILE_COMPLETED");
        }
        IdentityInboxReceipt applied = new IdentityInboxReceipt();
        applied.setEventId(event.eventId());
        applied.setEventType(event.eventType());
        applied.setPrincipalId(event.principalId());
        applied.setPayloadFingerprint(fingerprint);
        applied.setProcessedAt(LocalDateTime.now());
        receipts.save(applied);
    }

    private void transition(AuthAccount account, IdentityLifecycleStatus target) {
        if (account.getLifecycleStatus() != target) {
            account.setLifecycleStatus(target);
            account.setLifecycleVersion((account.getLifecycleVersion() == null ? 0L : account.getLifecycleVersion()) + 1L);
        }
    }

    private static String fingerprint(String raw) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
