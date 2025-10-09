package com.delivery.match_service.listener;

import com.delivery.match_service.dto.event.FindShipperEvent;
import com.delivery.match_service.dto.event.ShipperFoundEvent;
import com.delivery.match_service.dto.request.FindNearbyShippersRequest;
import com.delivery.match_service.dto.response.NearbyShipperResponse;
import com.delivery.match_service.service.MatchService;
import com.delivery.match_service.service.MatchEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ✅ Test Continuous Shipper Search với Simplified Event Architecture
 * Chỉ test ShipperFoundEvent publishing, không có MatchEventService
 */
@ExtendWith(MockitoExtension.class)
class FindShipperEventListenerTest {

    @Mock
    private MatchService matchService;

    @Mock
    private MatchEventPublisher matchEventPublisher;

    @Mock
    private Acknowledgment acknowledgment;

    private FindShipperEventListener listener;

    private FindShipperEvent testEvent;

    @BeforeEach
    void setUp() {
        // ✅ Constructor Injection với simplified dependencies
        listener = new FindShipperEventListener(matchService, matchEventPublisher);

        // Setup test event
        testEvent = new FindShipperEvent();
        testEvent.setDeliveryId(123L);
        testEvent.setOrderId(456L);
        testEvent.setPickupLat(10.762622);
        testEvent.setPickupLng(106.660172);
        testEvent.setDeliveryLat(10.775000);
        testEvent.setDeliveryLng(106.700000);
        testEvent.setRestaurantName("Test Restaurant");
        testEvent.setPickupAddress("123 Pickup St");
        testEvent.setDeliveryAddress("456 Delivery Ave");
    }

    @Test
    void testHandleFindShipperEvent_SuccessOnFirstAttempt() {
        // Given
        NearbyShipperResponse shipper1 = createTestShipper(1L, 10.763000, 106.661000);
        NearbyShipperResponse shipper2 = createTestShipper(2L, 10.764000, 106.662000);
        List<NearbyShipperResponse> foundShippers = List.of(shipper1, shipper2);

        when(matchService.findNearbyShippers(any(FindNearbyShippersRequest.class), anyLong(), anyString()))
                .thenReturn(Mono.just(foundShippers));

        // When
        listener.handleFindShipperEvent(testEvent, "test-topic", 0, System.currentTimeMillis(), acknowledgment);

        // Then - Verify ShipperFoundEvent is published
        verify(matchEventPublisher, timeout(1000)).publishShipperFoundEvent(any(ShipperFoundEvent.class));
        verify(acknowledgment, timeout(1000)).acknowledge();
    }

    @Test
    void testHandleFindShipperEvent_SuccessAfterRetry() {
        // Given
        NearbyShipperResponse shipper1 = createTestShipper(1L, 10.763000, 106.661000);
        List<NearbyShipperResponse> foundShippers = List.of(shipper1);

        when(matchService.findNearbyShippers(any(FindNearbyShippersRequest.class), anyLong(), anyString()))
                .thenReturn(Mono.just(Collections.emptyList()))  // First call - empty
                .thenReturn(Mono.just(foundShippers));           // Second call - success

        // When
        listener.handleFindShipperEvent(testEvent, "test-topic", 0, System.currentTimeMillis(), acknowledgment);

        // Then - Verify ShipperFoundEvent is published after retry
        verify(matchEventPublisher, timeout(35000)).publishShipperFoundEvent(any(ShipperFoundEvent.class));
        verify(acknowledgment, timeout(35000)).acknowledge();
    }

    @Test
    void testHandleFindShipperEvent_FailAfterMaxRetries() {
        // Given - Always return empty list
        when(matchService.findNearbyShippers(any(FindNearbyShippersRequest.class), anyLong(), anyString()))
                .thenReturn(Mono.just(Collections.emptyList()));

        // When
        listener.handleFindShipperEvent(testEvent, "test-topic", 0, System.currentTimeMillis(), acknowledgment);

        // Then - Should publish ShipperNotFoundEvent after max retries
        verify(matchEventPublisher, timeout(60000)).publishShipperNotFoundEvent(any());
        verify(acknowledgment, timeout(60000)).acknowledge();
        // Should not publish ShipperFoundEvent
        verify(matchEventPublisher, never()).publishShipperFoundEvent(any());
    }

    @Test
    void testHandleFindShipperEvent_SystemError() {
        // Given
        when(matchService.findNearbyShippers(any(FindNearbyShippersRequest.class), anyLong(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("System error")));

        // When
        listener.handleFindShipperEvent(testEvent, "test-topic", 0, System.currentTimeMillis(), acknowledgment);

        // Then - Should not retry system errors, publish ShipperNotFoundEvent
        verify(matchEventPublisher, timeout(5000)).publishShipperNotFoundEvent(any());
        verify(acknowledgment, timeout(5000)).acknowledge();
        // Should not publish ShipperFoundEvent
        verify(matchEventPublisher, never()).publishShipperFoundEvent(any());
    }

    @Test
    void testHandleFindShipperEvent_InvalidEvent() {
        // Given
        FindShipperEvent invalidEvent = new FindShipperEvent();
        invalidEvent.setDeliveryId(null); // Invalid event

        // When
        listener.handleFindShipperEvent(invalidEvent, "test-topic", 0, System.currentTimeMillis(), acknowledgment);

        // Then
        verify(acknowledgment, timeout(1000)).acknowledge();
        verifyNoInteractions(matchService);
        verifyNoInteractions(matchEventPublisher);
    }

    @Test 
    void testCreateFindShippersRequest_WithPickupLocation() {
        // Given
        when(matchService.findNearbyShippers(any(FindNearbyShippersRequest.class), anyLong(), anyString()))
                .thenAnswer(invocation -> {
                    FindNearbyShippersRequest request = invocation.getArgument(0);
                    // Verify that pickup location is used
                    assert Double.compare(request.getLatitude(), 10.762622) == 0;
                    assert Double.compare(request.getLongitude(), 106.660172) == 0;
                    assert Double.compare(request.getRadiusKm(), 5.0) == 0;
                    assert request.getMaxShippers() == 10;
                    
                    return Mono.just(List.of(createTestShipper(1L, 10.763000, 106.661000)));
                });
        
        // When
        listener.handleFindShipperEvent(testEvent, "test-topic", 0, System.currentTimeMillis(), acknowledgment);

        // Then
        verify(matchService, timeout(1000)).findNearbyShippers(any(FindNearbyShippersRequest.class), anyLong(), anyString());
        verify(matchEventPublisher, timeout(1000)).publishShipperFoundEvent(any(ShipperFoundEvent.class));
        verify(acknowledgment, timeout(1000)).acknowledge();
    }

    private NearbyShipperResponse createTestShipper(Long shipperId, Double lat, Double lng) {
        NearbyShipperResponse shipper = new NearbyShipperResponse();
        shipper.setShipperId(shipperId);
        shipper.setShipperName("Shipper " + shipperId);
        shipper.setShipperPhone("090123456" + shipperId);
        shipper.setLatitude(lat);
        shipper.setLongitude(lng);
        shipper.setDistanceKm(1.2);
        shipper.setOnline(true);
        return shipper;
    }
}
