package com.delivery.order.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.identity.contracts.SimulationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderContractsSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final UUID eventId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID secondId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final LocalDateTime timestamp = LocalDateTime.of(2026, 9, 27, 12, 30, 45);
    private final SimulationContext simulationContext = new SimulationContext(
            SimulationContext.ExecutionMode.SIMULATION, eventId, secondId, 3L);

    @Test
    void roundTripsOrderCreatedEvent() throws Exception {
        var source = new OrderCreatedEvent(
                2, eventId, 20L, 30L, 31L, 40L, "PENDING", money("100.00"),
                money("5.00"), money("10.00"), money("105.00"), money("3.00"),
                money("1.00"), money("9.00"), money("10.00"), money("2.00"), money("4.00"),
                "COD", "Dropoff", 10.78, 106.68, 10.76, 106.66, "Restaurant",
                "Restaurant address", "0900000000", "Customer", "0911111111", "No onions",
                timestamp, 30L, 31L, eventId, secondId,
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                List.of(Map.of("itemId", 1, "quantity", 2)),
                List.of(Map.of("code", "WELCOME", "amount", "5.00")),
                simulationContext, "ORDER_CREATED", timestamp);

        assertRoundTrip(source, OrderCreatedEvent.class);
    }

    @Test
    void roundTripsPaymentEvent() throws Exception {
        var source = new PaymentEvent(
                1L, 20L, 30L, "COMPLETED", 105.0, "CARD", "txn-1", timestamp, null);

        assertRoundTrip(source, PaymentEvent.class);
    }

    @Test
    void roundTripsDeliveryStatusUpdatedEvent() throws Exception {
        var source = new DeliveryStatusUpdatedEvent(
                10L, 20L, 50L, "IN_PROGRESS", "ASSIGNED", "IN_PROGRESS", "ASSIGNED",
                timestamp, timestamp, "DELIVERY_STATUS_UPDATED", "Picked up", 10.77, 106.67,
                timestamp.plusMinutes(20), simulationContext);

        assertRoundTrip(source, DeliveryStatusUpdatedEvent.class);
    }

    @Test
    void roundTripsRestaurantEvent() throws Exception {
        var source = new RestaurantEvent(
                eventId, 40L, 30L, 20L, "CONFIRMED", "CONFIRM", 25,
                null, timestamp, "Kitchen accepted");

        assertRoundTrip(source, RestaurantEvent.class);
    }

    @Test
    void roundTripsShipperEvent() throws Exception {
        var source = new ShipperEvent(
                50L, 10L, 20L, "ACCEPTED", "On my way", null, timestamp, 8.5,
                10.77, 106.67, simulationContext);

        assertRoundTrip(source, ShipperEvent.class);
    }

    private <T> void assertRoundTrip(T source, Class<T> type) throws Exception {
        byte[] json = mapper.writeValueAsBytes(source);

        assertThat(mapper.readValue(json, type)).isEqualTo(source);
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
