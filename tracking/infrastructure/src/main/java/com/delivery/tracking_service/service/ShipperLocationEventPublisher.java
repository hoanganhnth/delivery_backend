package com.delivery.tracking_service.service;

import com.delivery.tracking_service.common.constants.KafkaTopicConstants;
import com.delivery.tracking_service.dto.event.ShipperLocationUpdatedEvent;
import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import com.delivery.tracking_service.repository.ShipperDeliveryAssignmentStore;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;
import com.delivery.identity.contracts.SimulationContext;

/**
 * ✅ Publisher gửi sự kiện vị trí shipper qua Kafka
 * Match-service sẽ consume để duy trì bản sao Redis Geo local
 */
@Slf4j
@Service
public class ShipperLocationEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ShipperDeliveryAssignmentStore assignments;

    @Autowired
    public ShipperLocationEventPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                         ShipperDeliveryAssignmentStore assignments) {
        this.kafkaTemplate = kafkaTemplate;
        this.assignments = assignments;
    }

    public ShipperLocationEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this(kafkaTemplate, null);
    }

    /**
     * Publish sự kiện cập nhật vị trí shipper
     * Chỉ gửi dữ liệu tối thiểu: shipperId, lat, lng, isOnline
     */
    public void publishLocationUpdate(Long shipperId, Double latitude, Double longitude, Boolean isOnline) {
        ShipperLocationResponse location = new ShipperLocationResponse();
        location.setShipperId(shipperId);
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setIsOnline(isOnline);
        publishLocationUpdate(location, "UNKNOWN");
    }

    public void publishLocationUpdate(ShipperLocationResponse location, String source) {
        publishLocationUpdate(location, source, SimulationContext.real());
    }

    public void publishLocationUpdate(ShipperLocationResponse location, String source,
                                      SimulationContext simulationContext) {
        Long shipperId = location.getShipperId();
        try {
            ShipperLocationUpdatedEvent event = new ShipperLocationUpdatedEvent(
                    shipperId, location.getLatitude(), location.getLongitude(), location.getIsOnline(),
                    System.currentTimeMillis(), java.util.UUID.randomUUID(),
                    assignments == null ? null : assignments.activeDelivery(shipperId).orElse(null),
                    location.getAccuracy(), location.getSpeed(), location.getHeading(), source
            );
            event.setSimulationContext(SimulationContext.orReal(simulationContext));

            var result = kafkaTemplate.send(
                    KafkaTopicConstants.SHIPPER_LOCATION_UPDATED_TOPIC,
                    shipperId.toString(),
                    event
            ).get(5, TimeUnit.SECONDS);
            log.debug("📡 Published location update for shipper {} to partition {}",
                    shipperId, result.getRecordMetadata().partition());

        } catch (Exception e) {
            log.error("💥 Error publishing location update for shipper {}: {}", shipperId, e.getMessage());
            throw new IllegalStateException("Cannot replicate shipper location", e);
        }
    }
}
