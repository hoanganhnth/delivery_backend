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
        var adapter = new RedisLocationFanoutAdapter(redis, assignments, objectMapper);
        LocationFanoutPublisher publisher = new LocationFanoutPublisher(
                new com.delivery.tracking.application.DefaultLocationFanoutUseCase(adapter, adapter));
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
    @Test
    void everyWireFactSurvivesCoreAndAdapterForBatchAndNullableOffline() throws Exception {
        var redis = mock(StringRedisTemplate.class);
        var assignments = mock(ShipperDeliveryAssignmentStore.class);
        var mapper = new ObjectMapper();
        var adapter = new RedisLocationFanoutAdapter(redis, assignments, mapper);
        var publisher = new LocationFanoutPublisher(new com.delivery.tracking.application.DefaultLocationFanoutUseCase(adapter, adapter));
        var facts = new com.delivery.tracking.application.api.FanoutLocation(42L,10.77,106.7,4.25,8.5,180.0,true,"ping","updated",1.2);
        when(assignments.activeDeliveries(42L)).thenReturn(java.util.Set.of(100L,101L));
        publisher.publish(FanoutLocationMapper.toResponse(facts));
        var payload = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(redis,times(2)).convertAndSend(eq(LocationFanoutPublisher.CHANNEL),payload.capture());
        var deliveries = new java.util.HashSet<Long>();
        for (String value : payload.getAllValues()) {
            var tree = mapper.readTree(value); deliveries.add(tree.get("deliveryId").asLong());
            var wire = mapper.treeToValue(tree.get("location"),ShipperLocationResponse.class);
            assertThat(FanoutLocationMapper.from(wire)).isEqualTo(facts);
        }
        assertThat(deliveries).containsExactlyInAnyOrder(100L,101L);
        var offline = new com.delivery.tracking.application.api.FanoutLocation(42L,null,null,null,null,null,false,"ping","updated",null);
        assertThat(FanoutLocationMapper.from(FanoutLocationMapper.toResponse(offline))).isEqualTo(offline);
        assertThat(FanoutLocationMapper.from(null)).isNull();
    }

}
