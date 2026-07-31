package com.delivery.flashsale_service.listener;

import com.delivery.flashsale_service.service.FlashSaleStockService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.util.UUID;

@Component @RequiredArgsConstructor
@ConditionalOnProperty(name = "app.flashsale.checkout-enabled", havingValue = "true")
public class OrderReservationEventListener {
    private final FlashSaleStockService stockService;
    private final ObjectMapper objectMapper;
    @Value("${app.kafka.topics.order-created:order.created}") private String orderCreatedTopic;

    @KafkaListener(topics = {"${app.kafka.topics.order-created:order.created}",
            "${app.kafka.topics.order-cancelled:order.cancelled}"})
    public void consume(String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                        Acknowledgment acknowledgment) throws Exception {
        JsonNode event = objectMapper.readTree(payload);
        requireText(event, "eventId");
        JsonNode node = event.get("flashSaleReservationId");
        if (node != null && !node.isNull()) {
            UUID reservationId = UUID.fromString(node.asText());
            long orderId = requirePositiveLong(event, "orderId");
            if (orderCreatedTopic.equals(topic))
                stockService.commit(reservationId, orderId);
            else stockService.release(reservationId, orderId);
        }
        acknowledgment.acknowledge();
    }

    private String requireText(JsonNode event, String field) {
        JsonNode value = event.get(field);
        if (value == null || value.asText().isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.asText();
    }
    private long requirePositiveLong(JsonNode event, String field) {
        JsonNode value = event.get(field);
        if (value == null || !value.canConvertToLong() || value.asLong() <= 0)
            throw new IllegalArgumentException(field + " must be positive");
        return value.asLong();
    }
}
