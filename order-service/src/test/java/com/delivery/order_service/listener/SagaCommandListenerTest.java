package com.delivery.order_service.listener;

import com.delivery.order_service.service.OrderEventService;
import com.delivery.order_service.service.OrderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.argThat;

@ExtendWith(MockitoExtension.class)
class SagaCommandListenerTest {

    @Mock OrderEventService orderEventService;
    @Mock OrderService orderService;
    @Mock Acknowledgment acknowledgment;

    private SagaCommandListener listener;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        listener = new SagaCommandListener(orderEventService, orderService);
    }

    @Test
    void invalidCommandIsNotAcknowledged() {
        assertThrows(IllegalStateException.class,
                () -> listener.handleUpdateOrderStatusCommand(
                        json("{\"sagaStatus\":\"DELIVERED\"}"), acknowledgment));

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void serviceFailureIsNotAcknowledged() {
        org.mockito.Mockito.doThrow(new IllegalStateException("invalid transition"))
                .when(orderEventService).handleDeliveryStatusUpdate(any());

        assertThrows(IllegalStateException.class,
                () -> listener.handleUpdateOrderStatusCommand(
                        json(command("DELIVERED", 1L)),
                        acknowledgment));

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void unknownStatusIsNotAcknowledgedAsSuccess() {
        assertThrows(IllegalStateException.class,
                () -> listener.handleUpdateOrderStatusCommand(
                        json(command("TYPO_STATUS", 1L)),
                        acknowledgment));

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void nonPositiveOrderIdentityIsNotAcknowledged() {
        assertThrows(IllegalStateException.class,
                () -> listener.handleUpdateOrderStatusCommand(
                        json(command("DELIVERED", 0L)),
                        acknowledgment));

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void successfulCommandIsAcknowledgedOnce() {
        StringJsonMessageConverter converter = new StringJsonMessageConverter(objectMapper);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "saga.command.update-order-status", 0, 0L, "1",
                command("FINDING_SHIPPER", 1L,
                        "{\"orderId\":1,\"deliveryId\":8,\"timestamp\":1785044842775}"));
        JsonNode converted = (JsonNode) converter
                .toMessage(record, null, null, JsonNode.class)
                .getPayload();

        listener.handleUpdateOrderStatusCommand(converted, acknowledgment);

        verify(orderEventService).handleDeliveryStatusUpdate(argThat(event ->
                Long.valueOf(1L).equals(event.getOrderId())
                        && Long.valueOf(8L).equals(event.getDeliveryId())
                        && "FINDING_SHIPPER".equals(event.getStatus())));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void malformedShipperNotFoundCorrelationIsNotAcknowledged() {
        assertThrows(IllegalStateException.class,
                () -> listener.handleUpdateOrderStatusCommand(
                        json(command("SHIPPER_NOT_FOUND", 1L, "not-json")),
                        acknowledgment));

        verify(orderService, never()).updateOrderStatusFromShipperNotFoundEvent(any());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void shipperNotFoundWithoutDeliveryIdentityIsNotAcknowledged() {
        assertThrows(IllegalStateException.class,
                () -> listener.handleUpdateOrderStatusCommand(
                        json(command("SHIPPER_NOT_FOUND", 1L, "{\"orderId\":1}")),
                        acknowledgment));

        verify(orderService, never()).updateOrderStatusFromShipperNotFoundEvent(any());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void contradictoryOriginalOrderIdentityIsNotAcknowledged() {
        assertThrows(IllegalStateException.class,
                () -> listener.handleUpdateOrderStatusCommand(
                        json(command("FINDING_SHIPPER", 1L,
                                "{\"orderId\":2,\"deliveryId\":8}")),
                        acknowledgment));

        verify(orderEventService, never()).handleDeliveryStatusUpdate(any());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void correlatedShipperNotFoundIsAcknowledged() {
        listener.handleUpdateOrderStatusCommand(
                json(command("SHIPPER_NOT_FOUND", 1L,
                        "{\"orderId\":1,\"deliveryId\":8,\"retryAttempts\":5}")),
                acknowledgment);

        verify(orderService).updateOrderStatusFromShipperNotFoundEvent(argThat(event ->
                Long.valueOf(1L).equals(event.getOrderId())
                        && Long.valueOf(8L).equals(event.getDeliveryId())
                        && Integer.valueOf(5).equals(event.getRetryAttempts())));
        verify(acknowledgment).acknowledge();
    }

    private String command(String status, Long orderId) {
        return command(status, orderId, "{}");
    }

    private String command(String status, Long orderId, String originalEvent) {
        String escapedOriginalEvent = originalEvent.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"eventId\":\"11111111-1111-1111-1111-111111111111\","
                + "\"orderId\":" + orderId + ",\"sagaStatus\":\"" + status
                + "\",\"originalEvent\":\"" + escapedOriginalEvent + "\"}";
    }

    private JsonNode json(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException(exception);
        }
    }
}
