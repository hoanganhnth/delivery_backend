package com.delivery.delivery_service.integration;

import com.delivery.delivery_service.service.DeliveryEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ✅ Test Integration cho Delivery Status Update Event với OrderId
 */
@ExtendWith(MockitoExtension.class)
class DeliveryStatusUpdateIntegrationTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private DeliveryEventPublisher deliveryEventPublisher;

    @Test
    void testPublishDeliveryStatusUpdatedWithOrderId() {
        // Given
        Long deliveryId = 123L;
        Long orderId = 456L;
        String newStatus = "DELIVERED";
        String oldStatus = "DELIVERING";

        CompletableFuture<SendResult<String, Object>> mockFuture = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(mockFuture);

        // When
        deliveryEventPublisher.publishDeliveryStatusUpdated(deliveryId, orderId, newStatus, oldStatus);

        // Then
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);

        verify(kafkaTemplate, times(1)).send(topicCaptor.capture(), keyCaptor.capture(), eventCaptor.capture());

        // Verify topic
        assertEquals("delivery.status-updated", topicCaptor.getValue());

        // Verify key (deliveryId)
        assertEquals(deliveryId.toString(), keyCaptor.getValue());

        // Verify event
        DeliveryEventPublisher.DeliveryStatusUpdateEvent event = 
            (DeliveryEventPublisher.DeliveryStatusUpdateEvent) eventCaptor.getValue();
        
        assertEquals(deliveryId, event.deliveryId);
        assertEquals(orderId, event.orderId); // ✅ Verify orderId is included
        assertEquals(newStatus, event.newStatus);
        assertEquals(oldStatus, event.oldStatus);
        assertEquals("DELIVERY_STATUS_UPDATED", event.eventType);
        assertNotNull(event.timestamp);
    }

    @Test
    void testEventStructureForOrderServiceCompatibility() {
        // Given
        Long deliveryId = 789L;
        Long orderId = 101112L;
        String newStatus = "ASSIGNED";
        String oldStatus = "PENDING";

        CompletableFuture<SendResult<String, Object>> mockFuture = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(mockFuture);

        // When
        deliveryEventPublisher.publishDeliveryStatusUpdated(deliveryId, orderId, newStatus, oldStatus);

        // Then
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(anyString(), anyString(), eventCaptor.capture());

        DeliveryEventPublisher.DeliveryStatusUpdateEvent event = 
            (DeliveryEventPublisher.DeliveryStatusUpdateEvent) eventCaptor.getValue();

        // ✅ Verify all required fields for Order Service
        assertNotNull(event.deliveryId, "deliveryId is required");
        assertNotNull(event.orderId, "orderId is required for order status update");
        assertNotNull(event.newStatus, "newStatus is required");
        assertNotNull(event.oldStatus, "oldStatus is required");
        assertNotNull(event.eventType, "eventType is required");
        assertNotNull(event.timestamp, "timestamp is required");

        // ✅ Verify event type
        assertEquals("DELIVERY_STATUS_UPDATED", event.eventType);
    }

    @Test
    void testDeliveryStatusMappingScenarios() {
        // Test các scenario mapping delivery status → order status
        CompletableFuture<SendResult<String, Object>> mockFuture = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(mockFuture);

        Long deliveryId = 555L;
        Long orderId = 666L;

        // Scenario 1: PENDING → ASSIGNED
        deliveryEventPublisher.publishDeliveryStatusUpdated(deliveryId, orderId, "ASSIGNED", "PENDING");
        
        // Scenario 2: ASSIGNED → PICKED_UP
        deliveryEventPublisher.publishDeliveryStatusUpdated(deliveryId, orderId, "PICKED_UP", "ASSIGNED");
        
        // Scenario 3: PICKED_UP → DELIVERING
        deliveryEventPublisher.publishDeliveryStatusUpdated(deliveryId, orderId, "DELIVERING", "PICKED_UP");
        
        // Scenario 4: DELIVERING → DELIVERED
        deliveryEventPublisher.publishDeliveryStatusUpdated(deliveryId, orderId, "DELIVERED", "DELIVERING");

        // Verify all events were sent
        verify(kafkaTemplate, times(4)).send(anyString(), anyString(), any());
    }
}

/**
 * ✅ Expected Order Service Mapping:
 * 
 * Delivery Status → Order Status
 * - PENDING → (no change, waiting for assignment)  
 * - ASSIGNED → ASSIGNED_TO_SHIPPER
 * - PICKED_UP → IN_DELIVERY
 * - DELIVERING → IN_DELIVERY  
 * - DELIVERED → DELIVERED
 * - CANCELLED → CANCELLED
 */
