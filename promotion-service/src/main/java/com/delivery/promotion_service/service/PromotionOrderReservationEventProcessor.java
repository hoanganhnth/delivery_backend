package com.delivery.promotion_service.service;

import com.delivery.promotion.domain.OrderReservationEventPolicy;
import com.delivery.promotion.application.ProcessOrderReservationEventUseCase;
import com.delivery.promotion.application.api.OrderReservationEventPort;
import com.delivery.promotion.application.api.PromotionCommands;

import com.delivery.promotion_service.dto.PromotionReservationResponse;
import com.delivery.promotion_service.dto.VoucherReservationResponse;
import com.delivery.promotion_service.entity.PromotionOrderReservationReceipt;
import com.delivery.promotion_service.exception.PromotionConflictException;
import com.delivery.promotion_service.repository.PromotionOrderReservationReceiptRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Owns the Kafka-to-voucher-reservation transaction. The receipt prevents a
 * second order event with a reused identity from committing/releasing another
 * reservation, and rolls back with the domain transition on failure.
 */
@Service
public class PromotionOrderReservationEventProcessor {

    private final PromotionService promotionService;
    private final PromotionOrderReservationReceiptRepository receipts;
    private final ObjectMapper objectMapper;
    private final String dataSourceUrl;
    private final String orderCreatedTopic;
    private final String orderCancelledTopic;
    private final String refundEligibleTopic;

    public PromotionOrderReservationEventProcessor(
            PromotionService promotionService,
            PromotionOrderReservationReceiptRepository receipts,
            ObjectMapper objectMapper,
            @Value("${spring.datasource.url:}") String dataSourceUrl,
            @Value("${app.kafka.topics.order-created:order.created}") String orderCreatedTopic,
            @Value("${app.kafka.topics.order-cancelled:order.cancelled}") String orderCancelledTopic,
            @Value("${app.kafka.topics.refund-eligible:order.refund-eligible}") String refundEligibleTopic) {
        this.promotionService = promotionService;
        this.receipts = receipts;
        this.objectMapper = objectMapper;
        this.dataSourceUrl = dataSourceUrl;
        this.orderCreatedTopic = orderCreatedTopic;
        this.orderCancelledTopic = orderCancelledTopic;
        this.refundEligibleTopic = refundEligibleTopic;
    }

    @Transactional
    public void process(String payload, String receivedTopic) throws Exception {
        JsonNode event = objectMapper.readTree(payload);
        UUID eventId = requiredUuid(event, "eventId");
        long orderId = requiredPositiveLong(event, "orderId");
        OrderReservationEventPolicy.Topic topic = OrderReservationEventPolicy.topic(
                receivedTopic, orderCreatedTopic, orderCancelledTopic, refundEligibleTopic);
        if (topic.failure() != null) throw new IllegalArgumentException(topic.failure());
        String sourceTopic = topic.source();
        String action = topic.action();
        UUID reservationId = optionalUuid(event, "voucherReservationId");
        UUID promotionReservationId = optionalUuid(event, "promotionReservationId");
        String fingerprint = fingerprint(payload);

        new ProcessOrderReservationEventUseCase().process(new PromotionCommands.OrderEvent(
                eventId, sourceTopic, action, orderId, reservationId, promotionReservationId,
                event.path("previousStatus").asText(""), fingerprint), new OrderReservationEventPort() {
            public boolean claim(PromotionCommands.OrderEvent command) {
                return insertIfAbsent(eventId, sourceTopic, action, orderId, reservationId, fingerprint) != 0;
            }
            public OrderReservationEventPolicy.Receipt existing(UUID id) {
                PromotionOrderReservationReceipt receipt = receipts.findById(id)
                        .orElseThrow(() -> new IllegalStateException(
                                "promotion receipt conflict resolved without a committed receipt"));
                return new OrderReservationEventPolicy.Receipt(receipt.getSourceTopic(), receipt.getAction(),
                        receipt.getOrderId(), receipt.getReservationId(), receipt.getPayloadFingerprint());
            }
            public String commit(UUID id, Long order, boolean bulk) {
                if (bulk) {
                    PromotionReservationResponse response = promotionService.commitPromotionReservation(id, order);
                    return response == null || response.state() == null ? null : response.state().name();
                }
                VoucherReservationResponse response = promotionService.commitReservation(id, order);
                return response == null || response.getState() == null ? null : response.getState().name();
            }
            public void release(UUID id, Long order, boolean bulk) {
                if (bulk) promotionService.releasePromotionReservation(id, order);
                else promotionService.releaseReservation(id, order);
            }
            public RuntimeException conflict(String message) { return new PromotionConflictException(message); }
        });
    }

    private int insertIfAbsent(UUID eventId, String sourceTopic, String action, long orderId,
                               UUID reservationId, String fingerprint) {
        if (dataSourceUrl != null && dataSourceUrl.startsWith("jdbc:h2:")) {
            return receipts.insertIfAbsentH2(eventId, sourceTopic, action, orderId, reservationId, fingerprint);
        }
        return receipts.insertIfAbsentPostgres(eventId, sourceTopic, action, orderId, reservationId, fingerprint);
    }

    private UUID requiredUuid(JsonNode event, String field) {
        JsonNode value = event.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return parseUuid(value.asText(), field);
    }

    private UUID optionalUuid(JsonNode event, String field) {
        JsonNode value = event.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.asText().isBlank()) {
            throw new IllegalArgumentException(field + " must be a UUID when present");
        }
        return parseUuid(value.asText(), field);
    }

    private UUID parseUuid(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(field + " must be a UUID", invalid);
        }
    }

    private long requiredPositiveLong(JsonNode event, String field) {
        JsonNode value = event.get(field);
        if (value == null || !value.canConvertToLong() || value.asLong() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value.asLong();
    }

    private String fingerprint(String payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

}
