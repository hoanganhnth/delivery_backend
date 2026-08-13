package com.delivery.flashsale_service.service;

import com.delivery.flashsale_service.entity.FlashSaleOrderReservationReceipt;
import com.delivery.flashsale_service.repository.FlashSaleOrderReservationReceiptRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * Owns the Kafka-to-flash-sale-stock transaction. A receipt and its stock
 * transition either commit together or roll back together for Kafka replay.
 */
@Service
@ConditionalOnProperty(name = "app.flashsale.checkout-enabled", havingValue = "true")
public class FlashSaleOrderReservationEventProcessor {

    private static final String COMMIT = "COMMIT";
    private static final String RELEASE = "RELEASE";

    private final FlashSaleStockService stockService;
    private final FlashSaleOrderReservationReceiptRepository receipts;
    private final ObjectMapper objectMapper;
    private final String dataSourceUrl;
    private final String orderCreatedTopic;
    private final String orderCancelledTopic;
    private final String refundEligibleTopic;

    public FlashSaleOrderReservationEventProcessor(
            FlashSaleStockService stockService,
            FlashSaleOrderReservationReceiptRepository receipts,
            ObjectMapper objectMapper,
            @Value("${spring.datasource.url:}") String dataSourceUrl,
            @Value("${app.kafka.topics.order-created:order.created}") String orderCreatedTopic,
            @Value("${app.kafka.topics.order-cancelled:order.cancelled}") String orderCancelledTopic,
            @Value("${app.kafka.topics.refund-eligible:order.refund-eligible}") String refundEligibleTopic) {
        this.stockService = stockService;
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
        String sourceTopic = canonicalSourceTopic(receivedTopic);
        String action = actionFor(sourceTopic);
        UUID reservationId = optionalUuid(event, "flashSaleReservationId");
        String fingerprint = fingerprint(payload);

        if (insertIfAbsent(eventId, sourceTopic, action, orderId, reservationId, fingerprint) == 0) {
            FlashSaleOrderReservationReceipt existing = receipts.findById(eventId)
                    .orElseThrow(() -> new IllegalStateException(
                            "flash-sale receipt conflict resolved without a committed receipt"));
            requireExactReplay(existing, sourceTopic, action, orderId, reservationId, fingerprint);
            return;
        }

        if (reservationId == null) {
            return;
        }
        if (COMMIT.equals(action)) {
            stockService.commit(reservationId, orderId);
        } else {
            stockService.release(reservationId, orderId);
        }
    }

    private int insertIfAbsent(UUID eventId, String sourceTopic, String action, long orderId,
                               UUID reservationId, String fingerprint) {
        if (dataSourceUrl != null && dataSourceUrl.startsWith("jdbc:h2:")) {
            return receipts.insertIfAbsentH2(eventId, sourceTopic, action, orderId, reservationId, fingerprint);
        }
        return receipts.insertIfAbsentPostgres(eventId, sourceTopic, action, orderId, reservationId, fingerprint);
    }

    private String canonicalSourceTopic(String receivedTopic) {
        if (receivedTopic == null || receivedTopic.isBlank()) {
            throw new IllegalArgumentException("source topic is required");
        }
        return receivedTopic.replaceFirst("-retry-flashsale-\\d+$", "");
    }

    private String actionFor(String sourceTopic) {
        if (orderCreatedTopic.equals(sourceTopic)) {
            return COMMIT;
        }
        if (orderCancelledTopic.equals(sourceTopic) || refundEligibleTopic.equals(sourceTopic)) {
            return RELEASE;
        }
        throw new IllegalArgumentException("Unexpected flash-sale reservation source topic: " + sourceTopic);
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

    private void requireExactReplay(FlashSaleOrderReservationReceipt receipt, String sourceTopic,
                                    String action, long orderId, UUID reservationId, String fingerprint) {
        if (!receipt.getSourceTopic().equals(sourceTopic)
                || !receipt.getAction().equals(action)
                || !receipt.getOrderId().equals(orderId)
                || !Objects.equals(receipt.getReservationId(), reservationId)
                || !receipt.getPayloadFingerprint().equals(fingerprint)) {
            throw new IllegalArgumentException(
                    "eventId replay has a contradictory flash-sale reservation payload");
        }
    }
}
