package com.delivery.auth_service.service;

import com.delivery.auth.application.api.IdentityProfileUseCase;
import com.delivery.identity.contracts.IdentityProfileCreated;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/** Kafka deserialization and listener retry boundary for the core profile workflow. */
@Component
@ConditionalOnProperty(name = "app.identity.events.enabled", havingValue = "true")
public class IdentityProfileEventListener {
    private final ObjectMapper mapper;
    private final IdentityProfileUseCase profiles;
    public IdentityProfileEventListener(ObjectMapper mapper, IdentityProfileUseCase profiles) {
        this.mapper=mapper;this.profiles=profiles;
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
    public void profileCreated(String raw) throws Exception {
        IdentityProfileCreated event=mapper.readValue(raw,IdentityProfileCreated.class);
        profiles.profileCreated(new IdentityProfileUseCase.Event(event.eventId(),event.eventType(),
                event.principalId(),event.profileId(),event.profileType()),TokenFingerprint.sha256(raw));
    }
}
