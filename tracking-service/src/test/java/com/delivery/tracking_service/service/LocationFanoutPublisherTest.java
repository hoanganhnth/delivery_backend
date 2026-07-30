package com.delivery.tracking_service.service;

import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import com.delivery.tracking_service.repository.ShipperDeliveryAssignmentStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocationFanoutPublisherTest {

    @Test
    void publishesExactDeliveryEnvelopeAndSkipsUnassignedLocation() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ShipperDeliveryAssignmentStore assignments = mock(ShipperDeliveryAssignmentStore.class);
        ObjectMapper objectMapper = new ObjectMapper();
        LocationFanoutPublisher publisher = new LocationFanoutPublisher(redis, assignments, objectMapper);
        ShipperLocationResponse location = new ShipperLocationResponse();
        location.setShipperId(42L);
        location.setLatitude(10.77);
        location.setLongitude(106.70);
        location.setIsOnline(true);

        when(assignments.activeDelivery(42L)).thenReturn(Optional.of(100L));
        publisher.publish(location);
        var payload = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(redis).convertAndSend(eq(LocationFanoutPublisher.CHANNEL), payload.capture());
        assertThat(payload.getValue()).contains("\"deliveryId\":100", "\"shipperId\":42");

        when(assignments.activeDelivery(42L)).thenReturn(Optional.empty());
        publisher.publish(location);
        verify(redis, times(1)).convertAndSend(
                eq(LocationFanoutPublisher.CHANNEL), org.mockito.ArgumentMatchers.any());
    }
}
