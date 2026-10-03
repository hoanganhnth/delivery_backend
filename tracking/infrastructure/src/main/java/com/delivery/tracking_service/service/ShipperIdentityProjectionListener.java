package com.delivery.tracking_service.service;

import com.delivery.identity.contracts.ShipperIdentityUpserted;
import com.delivery.tracking.application.api.ApplyShipperIdentityCommand;
import com.delivery.tracking.application.api.ShipperIdentityInboxUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
public class ShipperIdentityProjectionListener {
    private final ObjectMapper mapper;
    private final ShipperIdentityInboxUseCase inbox;
    public ShipperIdentityProjectionListener(ObjectMapper mapper, ShipperIdentityInboxUseCase inbox) {
        this.mapper = mapper; this.inbox = inbox;
    }
    @RetryableTopic(
            attempts = "${app.shipper.identity.retry.attempts:4}",
            backoff = @Backoff(delayExpression = "${app.shipper.identity.retry.initial-delay-ms:1000}",
                    multiplierExpression = "${app.shipper.identity.retry.multiplier:2.0}",
                    maxDelayExpression = "${app.shipper.identity.retry.max-delay-ms:10000}"),
            exclude = IllegalArgumentException.class,
            kafkaTemplate = "trackingRetryKafkaTemplate",
            autoCreateTopics = "${app.shipper.identity.retry.auto-create-topics:false}",
            retryTopicSuffix = "-retry-tracking-shipper-identity",
            dltTopicSuffix = ".tracking-shipper-identity.DLT")
    @KafkaListener(topics = "${app.shipper.identity-topic:shipper.identity.upserted}",
            groupId = "${app.shipper.identity-consumer-group:tracking-shipper-identity-v1}")
    public void upsert(String raw, Acknowledgment acknowledgment) throws Exception {
        ShipperIdentityUpserted event = mapper.readValue(raw, ShipperIdentityUpserted.class);
        inbox.apply(new ApplyShipperIdentityCommand(event.eventId(), event.eventType(), event.principalId(),
                event.legacyUserId(), event.shipperId(), event.mappingVersion(), raw));
        acknowledgment.acknowledge();
    }
}
