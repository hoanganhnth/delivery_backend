package com.delivery.delivery_service.listener;

import com.delivery.delivery_service.dto.event.OrderCreatedEvent;
import com.delivery.delivery_service.dto.event.OrderCancelledEvent;
import com.delivery.delivery_service.dto.event.ExpireShipperOfferCommand;
import com.delivery.delivery_service.dto.event.ShipperNotFoundEvent;
import com.delivery.delivery_service.dto.response.DeliveryResponse;
import com.delivery.delivery_service.service.DeliveryService;
import com.delivery.delivery_service.service.EventValidationService;
import com.delivery.delivery_service.service.OutboxService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderEventListenerValidationTest {

    @Test
    void anyValidationFailureBecomesCorrelatedFailureWithoutCreatingDelivery() throws Exception {
        DeliveryService deliveryService = mock(DeliveryService.class);
        OutboxService outboxService = mock(OutboxService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        OrderEventListener listener = new OrderEventListener(
                deliveryService, new EventValidationService(), outboxService);
        OrderCreatedEvent event = minimallyPopulatedEvent();
        event.setTotalPrice(BigDecimal.ZERO);

        listener.handleCreateDeliveryCommand(
                new ObjectMapper().findAndRegisterModules().writeValueAsString(event), acknowledgment);

        verifyNoInteractions(deliveryService);
        verify(outboxService).saveEvent(eq(event.getEventId()), eq("ORDER"), eq("101"), eq("DELIVERY_COMMAND_FAILED"),
                eq("delivery.created.failed"), eq("101"), any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void missingCanonicalShippingFeeBecomesCorrelatedFailureWithoutCreatingDelivery() throws Exception {
        DeliveryService deliveryService = mock(DeliveryService.class);
        OutboxService outboxService = mock(OutboxService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        OrderEventListener listener = new OrderEventListener(
                deliveryService, new EventValidationService(), outboxService);
        OrderCreatedEvent event = minimallyPopulatedEvent();
        event.setShippingFee(null);

        listener.handleCreateDeliveryCommand(
                new ObjectMapper().findAndRegisterModules().writeValueAsString(event), acknowledgment);

        verifyNoInteractions(deliveryService);
        verify(outboxService).saveEvent(eq(event.getEventId()), eq("ORDER"), eq("101"),
                eq("DELIVERY_COMMAND_FAILED"), eq("delivery.created.failed"), eq("101"), any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void canonicalPositiveDiscountCreatesDeliveryAndAcknowledges() throws Exception {
        DeliveryService deliveryService = mock(DeliveryService.class);
        OutboxService outboxService = mock(OutboxService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        OrderEventListener listener = new OrderEventListener(
                deliveryService, new EventValidationService(), outboxService);
        OrderCreatedEvent event = minimallyPopulatedEvent();
        event.setDiscountAmount(new BigDecimal("10000"));
        event.setTotalPrice(new BigDecimal("102000"));
        DeliveryResponse response = new DeliveryResponse();
        response.setId(202L);
        when(deliveryService.createDeliveryFromOrderEvent(any())).thenReturn(response);

        listener.handleCreateDeliveryCommand(
                new ObjectMapper().findAndRegisterModules().writeValueAsString(event), acknowledgment);

        verify(deliveryService).createDeliveryFromOrderEvent(argThat(value ->
                value.getDiscountAmount().compareTo(new BigDecimal("10000")) == 0
                        && value.getTotalPrice().compareTo(new BigDecimal("102000")) == 0));
        verify(acknowledgment).acknowledge();
        verifyNoInteractions(outboxService);
    }

    @Test
    void uncorrelatableMalformedCommandIsNotAcknowledged() {
        DeliveryService deliveryService = mock(DeliveryService.class);
        OutboxService outboxService = mock(OutboxService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        OrderEventListener listener = new OrderEventListener(
                deliveryService, new EventValidationService(), outboxService);

        assertThrows(IllegalArgumentException.class,
                () -> listener.handleCreateDeliveryCommand("not-json", acknowledgment));

        verifyNoInteractions(deliveryService, outboxService, acknowledgment);
    }

    @Test
    void correlatedCancelRejectionPublishesFailureThenAcknowledgesCommand() throws Exception {
        DeliveryService deliveryService = mock(DeliveryService.class);
        OutboxService outboxService = mock(OutboxService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        OrderEventListener listener = new OrderEventListener(
                deliveryService, new EventValidationService(), outboxService);
        OrderCancelledEvent event = new OrderCancelledEvent();
        event.setEventId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        event.setOrderId(101L);
        doThrow(new com.delivery.delivery_service.exception.InvalidStatusException("already picked up"))
                .when(deliveryService).cancelDeliveryFromOrderCancelledEvent(any());

        listener.handleCancelDeliveryCommand(
                new ObjectMapper().findAndRegisterModules().writeValueAsString(event), acknowledgment);

        verify(outboxService).saveEvent(eq(event.getEventId()), eq("ORDER"), eq("101"), eq("DELIVERY_COMMAND_FAILED"),
                eq("delivery.cancel.failed"), eq("101"), any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void offerTimeoutIsAcknowledgedOnlyAfterDeliveryConverges() throws Exception {
        DeliveryService deliveryService = mock(DeliveryService.class);
        OutboxService outboxService = mock(OutboxService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        OrderEventListener listener = new OrderEventListener(
                deliveryService, new EventValidationService(), outboxService);
        String command = """
                {"eventId":"44444444-4444-4444-4444-444444444444",
                 "orderId":101,"deliveryId":202,"timedOutShipperId":303,
                 "expectedOfferExpiresAt":"2026-07-25T13:03:00"}
                """;

        listener.handleExpireShipperOfferCommand(command, acknowledgment);

        verify(deliveryService).expireShipperOffer(argThat((ExpireShipperOfferCommand value) ->
                value.getOrderId().equals(101L)
                        && value.getTimedOutShipperId().equals(303L)));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void shipperNotFoundCommandUsesDedicatedTerminalTransition() throws Exception {
        DeliveryService deliveryService = mock(DeliveryService.class);
        OutboxService outboxService = mock(OutboxService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        OrderEventListener listener = new OrderEventListener(
                deliveryService, new EventValidationService(), outboxService);
        String command = """
                {"eventId":"55555555-5555-5555-5555-555555555555",
                 "orderId":101,"deliveryId":202,"retryAttempts":10}
                """;

        listener.handleMarkShipperNotFoundCommand(command, acknowledgment);

        verify(deliveryService).updateDeliveryStatusFromShipperNotFoundEvent(
                argThat((ShipperNotFoundEvent value) ->
                        value.getEventId().equals(UUID.fromString(
                                "55555555-5555-5555-5555-555555555555"))
                                && value.getOrderId().equals(101L)
                                && value.getDeliveryId().equals(202L)));
        verify(deliveryService, never()).cancelDeliveryFromOrderCancelledEvent(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void malformedShipperNotFoundCommandIsNotAcknowledged() {
        DeliveryService deliveryService = mock(DeliveryService.class);
        OutboxService outboxService = mock(OutboxService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        OrderEventListener listener = new OrderEventListener(
                deliveryService, new EventValidationService(), outboxService);

        assertThrows(IllegalArgumentException.class,
                () -> listener.handleMarkShipperNotFoundCommand(
                        "{\"orderId\":101,\"deliveryId\":202}", acknowledgment));

        verifyNoInteractions(deliveryService, outboxService, acknowledgment);
    }

    @Test
    void ackFailureAfterCommittedCreateDoesNotPublishFalseSagaFailure() throws Exception {
        DeliveryService deliveryService = mock(DeliveryService.class);
        OutboxService outboxService = mock(OutboxService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        OrderEventListener listener = new OrderEventListener(
                deliveryService, new EventValidationService(), outboxService);
        OrderCreatedEvent event = minimallyPopulatedEvent();
        DeliveryResponse response = new DeliveryResponse();
        response.setId(202L);
        when(deliveryService.createDeliveryFromOrderEvent(any())).thenReturn(response);
        doThrow(new IllegalStateException("commit offset unavailable"))
                .when(acknowledgment).acknowledge();

        assertThrows(IllegalStateException.class, () -> listener.handleCreateDeliveryCommand(
                new ObjectMapper().findAndRegisterModules().writeValueAsString(event), acknowledgment));

        verify(deliveryService).createDeliveryFromOrderEvent(any());
        verifyNoInteractions(outboxService);
    }

    @Test
    void ackFailureAfterCommittedCancellationDoesNotPublishFalseSagaFailure() throws Exception {
        DeliveryService deliveryService = mock(DeliveryService.class);
        OutboxService outboxService = mock(OutboxService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        OrderEventListener listener = new OrderEventListener(
                deliveryService, new EventValidationService(), outboxService);
        OrderCancelledEvent event = new OrderCancelledEvent();
        event.setEventId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        event.setOrderId(101L);
        doThrow(new IllegalStateException("commit offset unavailable"))
                .when(acknowledgment).acknowledge();

        assertThrows(IllegalStateException.class, () -> listener.handleCancelDeliveryCommand(
                new ObjectMapper().findAndRegisterModules().writeValueAsString(event), acknowledgment));

        verify(deliveryService).cancelDeliveryFromOrderCancelledEvent(any());
        verifyNoInteractions(outboxService);
    }

    @Test
    void nonPositiveCreateIdentityGoesToRetryDltWithoutUnusableFailureEvent() throws Exception {
        DeliveryService deliveryService = mock(DeliveryService.class);
        OutboxService outboxService = mock(OutboxService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        OrderEventListener listener = new OrderEventListener(
                deliveryService, new EventValidationService(), outboxService);
        OrderCreatedEvent event = minimallyPopulatedEvent();
        event.setOrderId(0L);

        assertThrows(IllegalArgumentException.class, () -> listener.handleCreateDeliveryCommand(
                new ObjectMapper().findAndRegisterModules().writeValueAsString(event), acknowledgment));

        verifyNoInteractions(deliveryService, outboxService, acknowledgment);
    }

    private OrderCreatedEvent minimallyPopulatedEvent() {
        OrderCreatedEvent event = new OrderCreatedEvent();
        event.setEventId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        event.setOrderId(101L);
        event.setUserId(7L);
        event.setRestaurantId(9L);
        event.setCreatorId(11L);
        event.setStatus("PENDING");
        event.setSubtotalPrice(new BigDecimal("100000"));
        event.setDiscountAmount(BigDecimal.ZERO);
        event.setShippingFee(new BigDecimal("12000"));
        event.setTotalPrice(new BigDecimal("112000"));
        event.setPaymentMethod("COD");
        event.setDeliveryAddress("123 Delivery Street");
        event.setRestaurantAddress("456 Restaurant Street");
        event.setPickupLat(10.76);
        event.setPickupLng(106.66);
        event.setDeliveryLat(10.78);
        event.setDeliveryLng(106.68);
        event.setCustomerName("Customer");
        event.setCustomerPhone("0900000000");
        return event;
    }
}
