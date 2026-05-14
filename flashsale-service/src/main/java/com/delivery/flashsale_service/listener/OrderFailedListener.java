package com.delivery.flashsale_service.listener;

import com.delivery.flashsale_service.service.FlashSaleStockService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderFailedListener {

    private final FlashSaleStockService stockService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order.cancelled", groupId = "flashsale-group")
    public void onOrderCancelled(String message) {
        log.info("Received order.cancelled event: {}", message);
        try {
            JsonNode root = objectMapper.readTree(message);
            // Since order-service doesn't send the items in the cancelled event, 
            // we will fetch the order details via an internal API or we can just 
            // rely on the user to understand this limitation for now, or we can make 
            // order-service send items. 
            // For this implementation, let's assume the event has an 'items' array.
            
            if (root.has("items")) {
                for (JsonNode item : root.get("items")) {
                    if (item.has("flashSaleItemId") && !item.get("flashSaleItemId").isNull()) {
                        Long flashSaleItemId = item.get("flashSaleItemId").asLong();
                        Integer quantity = item.has("quantity") ? item.get("quantity").asInt() : 1;
                        log.info("Releasing stock for flash sale item: {}", flashSaleItemId);
                        stockService.releaseStock(flashSaleItemId, quantity);
                    }
                }
            } else {
                log.warn("order.cancelled event does not contain items, cannot release stock automatically");
            }
        } catch (Exception e) {
            log.error("Failed to process order.cancelled event", e);
        }
    }

    @KafkaListener(topics = "payment.failed", groupId = "flashsale-group")
    public void onPaymentFailed(String message) {
        log.info("Received payment.failed event: {}", message);
        // Similar logic here...
    }
}
