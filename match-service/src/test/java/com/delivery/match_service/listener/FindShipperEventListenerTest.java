package com.delivery.match_service.listener;

import com.delivery.match_service.dto.event.FindShipperEvent;
import com.delivery.match_service.dto.request.FindNearbyShippersRequest;
import com.delivery.match_service.dto.response.NearbyShipperResponse;
import com.delivery.match_service.service.MatchService;
import com.delivery.match_service.service.MatchEventService;
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
 * ✅ Test Continuous Shipper Search với Retry Mechanism
 */
@ExtendWith(MockitoExtension.class)
class FindShipperEventListenerTest {

    @Mock
    private MatchService matchService;

    @Mock
    private MatchEventService matchEventService;

    @Mock
    private MatchEventPublisher matchEventPublisher;

    @Mock
    private Acknowledgment acknowledgment;

    private FindShipperEventListener listener;

    private FindShipperEvent testEvent;

    @BeforeEach
    void setUp() {
        // ✅ Constructor Injection (MANDATORY)
        listener = new FindShipperEventListener(matchService, matchEventService, matchEventPublisher);

        // Setup test event
        testEvent = new FindShipperEvent();
        testEvent.setDeliveryId(123L);
        testEvent.setOrderId(456L);
        testEvent.setPickupLat(10.762622);
        testEvent.setPickupLng(106.660172);
        testEvent.setDeliveryLat(10.775000);
        testEvent.setDeliveryLng(106.700000);
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

        // Then
        verify(matchEventService, timeout(1000)).processShipperMatchResult(testEvent, foundShippers);
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

        // Then
        verify(matchEventService, timeout(35000)).processShipperMatchResult(testEvent, foundShippers);
        verify(acknowledgment, timeout(35000)).acknowledge();
    }

    @Test
    void testHandleFindShipperEvent_FailAfterMaxRetries() {
        // Given - Always return empty list
        when(matchService.findNearbyShippers(any(FindNearbyShippersRequest.class), anyLong(), anyString()))
                .thenReturn(Mono.just(Collections.emptyList()));

        // When
        listener.handleFindShipperEvent(testEvent, "test-topic", 0, System.currentTimeMillis(), acknowledgment);

        // Then - Should eventually call with empty list after max retries
        verify(matchEventService, timeout(60000)).processShipperMatchResult(testEvent, Collections.emptyList());
        verify(acknowledgment, timeout(60000)).acknowledge();
    }

    @Test
    void testHandleFindShipperEvent_SystemError() {
        // Given
        when(matchService.findNearbyShippers(any(FindNearbyShippersRequest.class), anyLong(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("System error")));

        // When
        listener.handleFindShipperEvent(testEvent, "test-topic", 0, System.currentTimeMillis(), acknowledgment);

        // Then - Should not retry system errors, acknowledge immediately
        verify(matchEventService, timeout(5000)).processShipperMatchResult(testEvent, Collections.emptyList());
        verify(acknowledgment, timeout(5000)).acknowledge();
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
        verifyNoInteractions(matchEventService);
    }

    @Test 
    void testCreateFindShippersRequest_WithPickupLocation() {
        // When - Call private method through reflection or use public wrapper
        // This would test the createFindShippersRequest logic
        
        // Note: Since method is private, we test it indirectly through handleFindShipperEvent
                when(matchService.findNearbyShippers(any(FindNearbyShippersRequest.class), anyLong(), anyString()))
                .thenAnswer(invocation -> {
                    FindNearbyShippersRequest request = invocation.getArgument(0);
                    // Verify that pickup location is used
                    assert Double.compare(request.getLatitude(), 10.762622) == 0;
                    assert Double.compare(request.getLongitude(), 106.660172) == 0;
                    assert Double.compare(request.getRadiusKm(), 5.0) == 0;
                    assert request.getMaxShippers() == 10;
                    
                    return Mono.just(List.of(createTestShipper(1L, 10.763000, 106.661000)));
                });        // When
        listener.handleFindShipperEvent(testEvent, "test-topic", 0, System.currentTimeMillis(), acknowledgment);

        // Then
        verify(matchService, timeout(1000)).findNearbyShippers(any(FindNearbyShippersRequest.class), anyLong(), anyString());
        verify(acknowledgment, timeout(1000)).acknowledge();
    }

    private NearbyShipperResponse createTestShipper(Long shipperId, Double lat, Double lng) {
        NearbyShipperResponse shipper = new NearbyShipperResponse();
        shipper.setShipperId(shipperId);
        shipper.setLatitude(lat);
        shipper.setLongitude(lng);
        shipper.setDistanceKm(1.2);
        shipper.setOnline(true);
        return shipper;
    }
}
