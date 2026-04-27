package com.delivery.tracking_service.service;

import com.delivery.tracking_service.common.constants.KafkaTopicConstants;
import com.delivery.tracking_service.dto.event.ShipperLocationUpdatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * ✅ Publisher gửi sự kiện vị trí shipper qua Kafka
 * Match-service sẽ consume để duy trì bản sao Redis Geo local
 */
@Slf4j
@Service
public class ShipperLocationEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ShipperLocationEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publish sự kiện cập nhật vị trí shipper
     * Chỉ gửi dữ liệu tối thiểu: shipperId, lat, lng, isOnline
     */
    public void publishLocationUpdate(Long shipperId, Double latitude, Double longitude, Boolean isOnline) {
        try {
            ShipperLocationUpdatedEvent event = new ShipperLocationUpdatedEvent(
                    shipperId, latitude, longitude, isOnline, System.currentTimeMillis()
            );

            kafkaTemplate.send(
                    KafkaTopicConstants.SHIPPER_LOCATION_UPDATED_TOPIC,
                    shipperId.toString(),
                    event
            ).thenAccept(result -> {
                log.debug("📡 Published location update for shipper {} to partition {}",
                        shipperId, result.getRecordMetadata().partition());
            }).exceptionally(ex -> {
                log.error("💥 Failed to publish location update for shipper {}: {}",
                        shipperId, ex.getMessage());
                return null;
            });

        } catch (Exception e) {
            log.error("💥 Error publishing location update for shipper {}: {}", shipperId, e.getMessage());
        }
    }
}
