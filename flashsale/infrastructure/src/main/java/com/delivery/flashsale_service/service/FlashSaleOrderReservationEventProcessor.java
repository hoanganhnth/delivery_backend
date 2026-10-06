package com.delivery.flashsale_service.service;

import com.delivery.flashsale.application.EventUseCase;
import com.delivery.flashsale.application.api.EventPort;
import com.delivery.flashsale.domain.FlashSaleEventPolicy;
import com.delivery.flashsale.domain.FlashSaleEventPolicy.Receipt;
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
import java.util.UUID;

/**
 * Owns the Kafka-to-flash-sale-stock transaction. A receipt and its stock
 * transition either commit together or roll back together for Kafka replay.
 */
@Service
@ConditionalOnProperty(name = "app.flashsale.checkout-enabled", havingValue = "true")
public class FlashSaleOrderReservationEventProcessor {

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
        String sourceTopic = FlashSaleEventPolicy.canonicalSourceTopic(receivedTopic);
        String action = FlashSaleEventPolicy.actionFor(sourceTopic, orderCreatedTopic, orderCancelledTopic, refundEligibleTopic);
        UUID reservationId = optionalUuid(event, "flashSaleReservationId");
        String fingerprint = fingerprint(payload);

        new EventUseCase(new EventPort() {
            public int claim(UUID id, Receipt receipt) {
                return insertIfAbsent(id, receipt.sourceTopic(), receipt.action(), receipt.orderId(), receipt.reservationId(), receipt.fingerprint());
            }
            public java.util.Optional<Receipt> find(UUID id) {
                return receipts.findById(id).map(row -> new Receipt(row.getSourceTopic(), row.getAction(), row.getOrderId(),
                        row.getReservationId(), row.getPayloadFingerprint()));
            }
            public void commit(UUID id, Long order) { stockService.commit(id, order); }
            public void release(UUID id, Long order) { stockService.release(id, order); }
        }).process(eventId, new Receipt(sourceTopic, action, orderId, reservationId, fingerprint));
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
