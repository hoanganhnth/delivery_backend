package com.delivery.notification_service.listener;

import com.delivery.delivery.contracts.ShipperFoundEvent;
import com.delivery.notification_service.service.NotificationService;
import com.delivery.notification_service.exception.NotificationConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class NotificationIngressEquivalenceTest {
    static final String SIMULATION = """
        "simulationContext":{"mode":"SIMULATION","runId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
        "cohortId":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","bindingVersion":1}
        """;
    static final String EVENT = "11111111-1111-1111-1111-111111111111";
    final NotificationService service = mock(NotificationService.class);
    final Acknowledgment ack = mock(Acknowledgment.class);
    final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    ShipperFoundEvent offer(String selection, String context) throws Exception {
        return mapper.readValue("{\"eventId\":\""+EVENT+"\",\"deliveryId\":8,\"orderId\":7,\"restaurantName\":\" R \","
                + "\"pickupAddress\":\" P \",\"deliveryAddress\":\" D \",\"availableShippers\":"+selection+context+"}",ShipperFoundEvent.class);
    }
    @Test void validSimulationOfferStillAcknowledgesWithoutInboxOrPush() throws Exception {
        new MatchEventListener(service).handleShipperFoundEvent(offer("[{\"shipperId\":5,\"distanceKm\":1.25}]",","+SIMULATION),
                "delivery.shipper-offered",0,1L,ack);
        verifyNoInteractions(service); verify(ack).acknowledge();
    }
    @Test void realOfferReplayPreservesSelectedIdentityAndCanonicalWhitespace() throws Exception {
        var event = offer("[{\"shipperId\":5,\"distanceKm\":1.25}]",""); var listener = new MatchEventListener(service);
        listener.handleShipperFoundEvent(event,"delivery.shipper-offered",0,1L,ack);
        listener.handleShipperFoundEvent(event,"delivery.shipper-offered",0,2L,ack);
        verify(service,times(2)).sendShipperMatchFoundNotification(5L,7L," R "," P "," D ",1.25,EVENT);
        verify(ack,times(2)).acknowledge();
    }
    @Test void nullSelectedElementRetainsRetryableWrapperRatherThanPoisonAndNeverAcknowledges() throws Exception {
        var event = offer("[null]","");
        var failure = assertThrows(IllegalStateException.class,() -> new MatchEventListener(service)
                .handleShipperFoundEvent(event,"delivery.shipper-offered",0,1L,ack));
        assertEquals("Failed to process persisted shipper offer",failure.getMessage());
        assertInstanceOf(NullPointerException.class,failure.getCause()); verifyNoInteractions(service,ack);
    }
    @Test void validSimulationStatusStillDispatchesUnlikeOfferAndBlankNameIsNull() {
        new DeliveryEventListener(service).handleDeliveryStatusUpdatedEvent(
                "{\"eventId\":\""+EVENT+"\",\"deliveryId\":8,\"orderId\":7,\"userId\":42,"
                        +"\"userPrincipalId\":71,\"status\":\"ASSIGNED\",\"shipperName\":\"  \","+SIMULATION+"}",
                "delivery.status-updated",0,1L,ack);
        verify(service).sendDeliveryStatusNotification(UUID.fromString(EVENT),42L,71L,8L,"ASSIGNED",null);
        verify(ack).acknowledge();
    }
    @Test void conflictRemainsPoisonAndInvalidSimulationDoesNotDispatch() throws Exception {
        var event = offer("[{\"shipperId\":5,\"distanceKm\":1.25}]","");
        var conflict = new NotificationConflictException("conflict");
        doThrow(conflict).when(service).sendShipperMatchFoundNotification(anyLong(),anyLong(),anyString(),anyString(),anyString(),anyDouble(),anyString());
        assertSame(conflict,assertThrows(NotificationConflictException.class,() -> new MatchEventListener(service)
                .handleShipperFoundEvent(event,"delivery.shipper-offered",0,1L,ack)));
        verifyNoInteractions(ack); clearInvocations(service);
        var invalid = offer("[{\"shipperId\":5,\"distanceKm\":1.25}]",",\"simulationContext\":{\"mode\":\"SIMULATION\"}");
        assertThrows(IllegalArgumentException.class,() -> new MatchEventListener(service)
                .handleShipperFoundEvent(invalid,"delivery.shipper-offered",0,1L,ack));
        verifyNoInteractions(service,ack);
    }
}
