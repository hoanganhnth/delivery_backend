package com.delivery.tracking_service.listener;

import com.delivery.tracking_service.common.constants.KafkaTopicConstants;
import com.delivery.tracking_service.dto.event.ShipperStatusChangeEvent;
import com.delivery.tracking_service.service.ShipperLocationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * ✅ Listener consume sự kiện thay đổi trạng thái shipper từ delivery-service
 * Thay thế REST call: delivery-service giờ publish Kafka event thay vì gọi REST
 */
@Slf4j
@Component
public class ShipperStatusEventListener {

    private final ShipperLocationService shipperLocationService;
    private final ObjectMapper objectMapper;

    public ShipperStatusEventListener(ShipperLocationService shipperLocationService) {
        this.shipperLocationService = shipperLocationService;
        this.objectMapper = new ObjectMapper();
    }

    @KafkaListener(topics = KafkaTopicConstants.SHIPPER_STATUS_CHANGE_TOPIC, groupId = "tracking-service")
    public void handleShipperStatusChange(String message) {
        try {
            ShipperStatusChangeEvent event = objectMapper.readValue(message, ShipperStatusChangeEvent.class);

            log.info("📥 Received shipper status change: shipper={}, status={}, delivery={}", 
                    event.getShipperId(), event.getStatus(), event.getDeliveryId());

            if ("BUSY".equalsIgnoreCase(event.getStatus())) {
                shipperLocationService.markShipperBusy(event.getShipperId());
            } else if ("AVAILABLE".equalsIgnoreCase(event.getStatus())) {
                shipperLocationService.markShipperAvailable(event.getShipperId());
            } else {
                log.warn("⚠️ Unknown shipper status: {}", event.getStatus());
            }

        } catch (Exception e) {
            log.error("💥 Error processing shipper status change event: {}", e.getMessage(), e);
        }
    }
}
