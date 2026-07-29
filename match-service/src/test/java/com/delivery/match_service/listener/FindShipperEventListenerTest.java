package com.delivery.match_service.listener;

import com.delivery.match_service.dto.event.FindShipperEvent;
import com.delivery.match_service.dto.event.ShipperFoundEvent;
import com.delivery.match_service.dto.request.FindNearbyShippersRequest;
import com.delivery.match_service.dto.response.NearbyShipperResponse;
import com.delivery.match_service.service.MatchService;
import com.delivery.match_service.service.MatchCancellationService;
import com.delivery.match_service.service.MatchEventPublisher;
import com.delivery.match_service.service.SettlementEligibilityClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    private FindShipperEventListener listener;

    private FindShipperEvent testEvent;
    @Mock
    private MatchCancellationService matchCancellationService;
    @Mock
    private SettlementEligibilityClient settlementEligibilityClient;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // ✅ Constructor Injection với simplified dependencies
        listener = new FindShipperEventListener(
                matchService, matchEventPublisher, matchCancellationService, settlementEligibilityClient, 20);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        // Setup test event
        testEvent = new FindShipperEvent();
        testEvent.setEventId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        testEvent.setDeliveryId(123L);
        testEvent.setOrderId(456L);
        testEvent.setPickupLat(10.762622);
        testEvent.setPickupLng(106.660172);
        testEvent.setDeliveryLat(10.775000);
        testEvent.setDeliveryLng(106.700000);
        testEvent.setRestaurantName("Test Restaurant");
        testEvent.setPickupAddress("123 Pickup St");
        testEvent.setDeliveryAddress("456 Delivery Ave");
        testEvent.setMaxRetryAttempts(1);
        testEvent.setInitialDelaySeconds(1);
        testEvent.setMaxDelaySeconds(1);
        testEvent.setTotalPrice(new BigDecimal("120000"));
        testEvent.setPaymentMethod("COD");
        lenient().when(matchService.tryReserveShipperOffer(anyLong(), anyLong(), anyInt())).thenReturn(true);
        lenient().when(settlementEligibilityClient.isCodEligible(anyLong(), any(BigDecimal.class)))
                .thenReturn(Mono.just(true));
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
        try {
            String json = objectMapper.writeValueAsString(testEvent);
            listener.handleFindShipperEvent(json, "test-topic", 0, System.currentTimeMillis()).block();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Then - Verify ShipperFoundEvent is published
        var eventCaptor = org.mockito.ArgumentCaptor.forClass(ShipperFoundEvent.class);
        verify(matchEventPublisher).publishShipperFoundEvent(eventCaptor.capture());
        assertEquals(outcomeEventId("shipper-found"), eventCaptor.getValue().getEventId());
        assertEquals(testEvent.getEventId().toString(), eventCaptor.getValue().getMatchingSessionId());
        assertEquals(1, eventCaptor.getValue().getAvailableShippers().size());
        assertEquals(1L, eventCaptor.getValue().getAvailableShippers().get(0).getShipperId());
    }

    @Test
    void testHandleFindShipperEvent_SuccessAfterRetry() {
        // Given
        NearbyShipperResponse shipper1 = createTestShipper(1L, 10.763000, 106.661000);
        List<NearbyShipperResponse> foundShippers = List.of(shipper1);

        AtomicInteger subscriptions = new AtomicInteger();
        when(matchService.findNearbyShippers(any(FindNearbyShippersRequest.class), anyLong(), anyString()))
                .thenReturn(Mono.defer(() -> subscriptions.getAndIncrement() == 0
                        ? Mono.just(Collections.emptyList())
                        : Mono.just(foundShippers)));

        // When
        try {
            String json = objectMapper.writeValueAsString(testEvent);
            listener.handleFindShipperEvent(json, "test-topic", 0, System.currentTimeMillis()).block();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Then - Verify ShipperFoundEvent is published after retry
        verify(matchEventPublisher).publishShipperFoundEvent(any(ShipperFoundEvent.class));
    }

    @Test
    void testHandleFindShipperEvent_FailAfterMaxRetries() {
        // Given - Always return empty list
        when(matchService.findNearbyShippers(any(FindNearbyShippersRequest.class), anyLong(), anyString()))
                .thenReturn(Mono.just(Collections.emptyList()));

        // When
        try {
            String json = objectMapper.writeValueAsString(testEvent);
            listener.handleFindShipperEvent(json, "test-topic", 0, System.currentTimeMillis()).block();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Then - Should publish ShipperNotFoundEvent after max retries
        var eventCaptor = org.mockito.ArgumentCaptor.forClass(
                com.delivery.match_service.dto.event.ShipperNotFoundEvent.class);
        verify(matchEventPublisher).publishShipperNotFoundEvent(eventCaptor.capture());
        assertEquals(outcomeEventId("shipper-not-found"), eventCaptor.getValue().getEventId());
        // Should not publish ShipperFoundEvent
        verify(matchEventPublisher, never()).publishShipperFoundEvent(any());
    }

    @Test
    void testHandleFindShipperEvent_SystemError() {
        // Given
        when(matchService.findNearbyShippers(any(FindNearbyShippersRequest.class), anyLong(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("System error")));

        String json;
        try {
            json = objectMapper.writeValueAsString(testEvent);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Infrastructure failure must not be converted to a business terminal event.
        assertThrows(RuntimeException.class,
                () -> listener.handleFindShipperEvent(
                        json, "test-topic", 0, System.currentTimeMillis()).block());
        verify(matchEventPublisher, never()).publishShipperNotFoundEvent(any());
        verify(matchEventPublisher, never()).publishShipperFoundEvent(any());
    }

    @Test
    void testHandleFindShipperEvent_InvalidEvent() {
        // Given
        FindShipperEvent invalidEvent = new FindShipperEvent();
        invalidEvent.setDeliveryId(null); // Invalid event

        String json;
        try {
            json = objectMapper.writeValueAsString(invalidEvent);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertThrows(IllegalStateException.class,
                () -> listener.handleFindShipperEvent(
                        json, "test-topic", 0, System.currentTimeMillis()));

        // Then
        verifyNoInteractions(matchService);
        verifyNoInteractions(matchEventPublisher);
    }

    @Test
    void missingCanonicalPickupIsRejectedInsteadOfMatchingAroundDeliveryAddress() throws Exception {
        testEvent.setPickupLat(null);
        testEvent.setPickupLng(null);

        String json = objectMapper.writeValueAsString(testEvent);

        assertThrows(IllegalStateException.class,
                () -> listener.handleFindShipperEvent(
                        json, "test-topic", 0, System.currentTimeMillis()));
        verifyNoInteractions(matchService, matchEventPublisher);
    }

    @Test
    void outOfCountryPickupIsRejectedBeforeQueryingGeoReplica() throws Exception {
        testEvent.setPickupLat(40.7128);
        testEvent.setPickupLng(-74.0060);

        String json = objectMapper.writeValueAsString(testEvent);

        assertThrows(IllegalStateException.class,
                () -> listener.handleFindShipperEvent(
                        json, "test-topic", 0, System.currentTimeMillis()));
        verifyNoInteractions(matchService, matchEventPublisher);
    }

    @Test
    void missingCanonicalDisplayFactsIsRejectedBeforeOfferReservation() throws Exception {
        testEvent.setRestaurantName(" ");

        String json = objectMapper.writeValueAsString(testEvent);

        assertThrows(IllegalStateException.class,
                () -> listener.handleFindShipperEvent(
                        json, "test-topic", 0, System.currentTimeMillis()));
        verifyNoInteractions(matchService, matchEventPublisher);
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
                    assert request.getMaxShippers() == 20;

                    return Mono.just(List.of(createTestShipper(1L, 10.763000, 106.661000)));
                });

        // When
        try {
            String json = objectMapper.writeValueAsString(testEvent);
            listener.handleFindShipperEvent(json, "test-topic", 0, System.currentTimeMillis()).block();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Then
        verify(matchService).findNearbyShippers(any(FindNearbyShippersRequest.class), anyLong(),
                anyString());
        verify(matchEventPublisher).publishShipperFoundEvent(any(ShipperFoundEvent.class));
    }

    @Test
    void skipsNearestIneligibleShipperAndOffersNextEligibleCandidate() throws Exception {
        NearbyShipperResponse first = createTestShipper(1L, 10.763000, 106.661000);
        NearbyShipperResponse second = createTestShipper(2L, 10.764000, 106.662000);
        when(matchService.findNearbyShippers(any(), anyLong(), anyString()))
                .thenReturn(Mono.just(List.of(first, second)));
        when(settlementEligibilityClient.isCodEligible(1L, testEvent.getTotalPrice()))
                .thenReturn(Mono.just(false));
        when(settlementEligibilityClient.isCodEligible(2L, testEvent.getTotalPrice()))
                .thenReturn(Mono.just(true));

        listener.handleFindShipperEvent(
                objectMapper.writeValueAsString(testEvent), "test-topic", 0,
                System.currentTimeMillis()).block();

        var eventCaptor = org.mockito.ArgumentCaptor.forClass(ShipperFoundEvent.class);
        verify(matchEventPublisher, timeout(1000)).publishShipperFoundEvent(eventCaptor.capture());
        assertEquals(2L, eventCaptor.getValue().getAvailableShippers().get(0).getShipperId());
        assertEquals(new BigDecimal("120000"), eventCaptor.getValue().getTotalPrice());
        verify(matchService).tryReserveShipperOffer(2L, 123L, 180);
    }

    @Test
    void settlementFailureIsInfrastructureFailureAndIsNotAcknowledged() throws Exception {
        testEvent.setMaxRetryAttempts(0);
        when(matchService.findNearbyShippers(any(), anyLong(), anyString()))
                .thenReturn(Mono.just(List.of(createTestShipper(1L, 10.763000, 106.661000))));
        when(settlementEligibilityClient.isCodEligible(1L, testEvent.getTotalPrice()))
                .thenReturn(Mono.error(new RuntimeException("settlement unavailable")));

        String json = objectMapper.writeValueAsString(testEvent);
        assertThrows(RuntimeException.class,
                () -> listener.handleFindShipperEvent(
                        json, "test-topic", 0, System.currentTimeMillis()).block());

        verify(matchEventPublisher, never()).publishShipperFoundEvent(any());
        verify(matchEventPublisher, never()).publishShipperNotFoundEvent(any());
    }

    @Test
    void cancelledDeliveryCannotBeResurrectedByDelayedFindCommand() throws Exception {
        when(matchCancellationService.isCancelled(123L)).thenReturn(true);

        listener.handleFindShipperEvent(
                objectMapper.writeValueAsString(testEvent), "test-topic", 0,
                System.currentTimeMillis()).block();

        verifyNoInteractions(matchService, matchEventPublisher);
    }

    @Test
    void cancellationAfterReservationReleasesOfferBeforeSkippingPublish() throws Exception {
        NearbyShipperResponse shipper = createTestShipper(1L, 10.763000, 106.661000);
        when(matchService.findNearbyShippers(any(), anyLong(), anyString()))
                .thenReturn(Mono.just(List.of(shipper)));
        when(matchCancellationService.isCancelled(123L)).thenReturn(false, false, true);

        listener.handleFindShipperEvent(
                objectMapper.writeValueAsString(testEvent), "test-topic", 0,
                System.currentTimeMillis()).block();

        verify(matchService).tryReserveShipperOffer(1L, 123L, 180);
        verify(matchService).releaseShipperOffer(1L, 123L);
        verify(matchEventPublisher, never()).publishShipperFoundEvent(any());
        verify(matchEventPublisher, never()).publishShipperNotFoundEvent(any());
    }

    @Test
    void cancellationStoreFailurePropagatesToKafkaRetry() throws Exception {
        when(matchCancellationService.isCancelled(123L))
                .thenThrow(new IllegalStateException("redis unavailable"));

        String json = objectMapper.writeValueAsString(testEvent);
        assertThrows(IllegalStateException.class,
                () -> listener.handleFindShipperEvent(
                        json, "test-topic", 0, System.currentTimeMillis()));

        verifyNoInteractions(matchService, matchEventPublisher);
    }

    @Test
    void doesNotInventShipperProfileFactsForGeoCandidate() throws Exception {
        NearbyShipperResponse shipper = createTestShipper(1L, 10.763000, 106.661000);
        shipper.setShipperName(null);
        shipper.setShipperPhone(null);
        when(matchService.findNearbyShippers(any(), anyLong(), anyString()))
                .thenReturn(Mono.just(List.of(shipper)));

        listener.handleFindShipperEvent(
                objectMapper.writeValueAsString(testEvent), "test-topic", 0,
                System.currentTimeMillis()).block();

        var eventCaptor = org.mockito.ArgumentCaptor.forClass(ShipperFoundEvent.class);
        verify(matchEventPublisher).publishShipperFoundEvent(eventCaptor.capture());
        var selected = eventCaptor.getValue().getAvailableShippers().get(0);
        assertNull(selected.getShipperName());
        assertNull(selected.getShipperPhone());
        assertNull(selected.getRating());
    }

    @Test
    void stopMatchingRedisFailureIsNotAcknowledged() {
        org.springframework.kafka.support.Acknowledgment acknowledgment =
                mock(org.springframework.kafka.support.Acknowledgment.class);
        doThrow(new IllegalStateException("redis unavailable"))
                .when(matchCancellationService).markCancelled(123L);

        assertThrows(IllegalStateException.class,
                () -> listener.handleStopMatchingCommand(
                        "{\"eventId\":\"22222222-2222-2222-2222-222222222222\","
                                + "\"orderId\":456,\"deliveryId\":123}", acknowledgment));

        verify(acknowledgment, never()).acknowledge();
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

    private String outcomeEventId(String outcome) {
        return UUID.nameUUIDFromBytes(("match:" + outcome + ":" + testEvent.getEventId())
                .getBytes(StandardCharsets.UTF_8)).toString();
    }
}
