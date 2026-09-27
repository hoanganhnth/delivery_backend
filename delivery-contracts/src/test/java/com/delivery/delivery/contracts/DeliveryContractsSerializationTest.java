package com.delivery.delivery.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.identity.contracts.SimulationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeliveryContractsSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final UUID eventId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID secondId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final LocalDateTime timestamp = LocalDateTime.of(2026, 9, 27, 12, 30, 45);
    private final SimulationContext simulationContext = new SimulationContext(
            SimulationContext.ExecutionMode.SIMULATION, eventId, secondId, 3L);

    @Test
    void roundTripsDeliveryCompletedEvent() throws Exception {
        var source = new DeliveryCompletedEvent(
                eventId, "DELIVERY_COMPLETED", simulationContext, 10L, 20L, 30L, 40L,
                money("12.34"), money("13.34"), money("11.34"), money("100.00"),
                money("4.00"), money("2.00"), money("1.00"), money("107.00"),
                money("10.49"), money("80.00"), money("20.00"), money("1.85"),
                money("21.85"), money("1.50"), timestamp, timestamp.minusMinutes(1),
                "1 Main Street", "COD", "Restaurant", "Customer");

        assertRoundTrip(source, DeliveryCompletedEvent.class);
    }

    @Test
    void roundTripsDeliveryExceptionReportedEvent() throws Exception {
        var source = new DeliveryExceptionReportedEvent(
                eventId, "DELIVERY_EXCEPTION_REPORTED", timestamp, secondId, 10L, 20L,
                30L, 31L, 40L, 50L, "IN_PROGRESS", "EXCEPTION_REPORTED",
                "OPEN", "Vehicle breakdown", "COD", money("100.00"), money("5.00"),
                money("10.00"), money("105.00"));

        assertRoundTrip(source, DeliveryExceptionReportedEvent.class);
    }

    @Test
    void roundTripsOrderCancelledEvent() throws Exception {
        var source = new OrderCancelledEvent(
                2, eventId, "ORDER_CANCELLED", timestamp, 20L, 30L, 31L, 40L,
                "PENDING", "CANCELLED", "Customer request", 30L, "CUSTOMER",
                "CUSTOMER_REQUEST", timestamp, 50L, true, secondId, eventId,
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                List.of(Map.of("itemId", 1, "quantity", 2)),
                List.of(Map.of("code", "WELCOME", "amount", "5.00")),
                timestamp.minusHours(1), timestamp, money("100.00"), money("5.00"),
                money("10.00"), money("105.00"), money("3.00"), money("1.00"),
                money("9.00"), money("10.00"), money("2.00"), money("4.00"), "COD");

        assertRoundTrip(source, OrderCancelledEvent.class);
    }

    @Test
    void roundTripsShipperFoundEventAndNestedRecords() throws Exception {
        var match = new ShipperFoundEvent.ShipperMatchResult(
                50L, "Shipper", "0900000000", 1.2, 10.76, 106.66, 4.8, true);
        var batchItem = new ShipperFoundEvent.BatchItem(
                10L, 20L, 1, 2, money("107.00"), secondId);
        var source = new ShipperFoundEvent(
                eventId, 10L, 20L, List.of(match), timestamp, 180, "session-1",
                "Restaurant", "Pickup", "Dropoff", 10.76, 106.66, 10.78, 106.68,
                money("107.00"), "COD", simulationContext, true, secondId,
                List.of(batchItem), List.of(eventId), 1);

        assertRoundTrip(source, ShipperFoundEvent.class);
    }

    @Test
    void roundTripsShipperNotFoundEvent() throws Exception {
        var source = new ShipperNotFoundEvent(
                eventId, 10L, 20L, "session-1", "No available shipper", timestamp,
                3, 10.0, 10.76, 106.66, 10.78, 106.68, simulationContext);

        assertRoundTrip(source, ShipperNotFoundEvent.class);
    }

    @Test
    void roundTripsOfferPersistedEvent() throws Exception {
        var source = new OfferPersistedEvent(
                eventId, secondId, 20L, 10L, "session-1", 50L, timestamp, "WAIT_SHIPPER_CONFIRM",
                simulationContext);

        assertRoundTrip(source, OfferPersistedEvent.class);
    }

    @Test
    void roundTripsOfferRetiredEvent() throws Exception {
        var source = new OfferRetiredEvent(eventId, secondId, 20L, 10L, "session-1", "EXPIRED", 50L);

        assertRoundTrip(source, OfferRetiredEvent.class);
    }

    @Test
    void roundTripsFindShipperEvent() throws Exception {
        var source = new FindShipperEvent(
                eventId, 10L, 20L, "Restaurant", "Pickup", 10.76, 106.66,
                "Dropoff", 10.78, 106.68, timestamp.plusMinutes(30), "Leave at door",
                timestamp, money("107.00"), "COD", "FIND_SHIPPER", timestamp,
                timestamp, 3, 1, 10, 2.0, timestamp.plusMinutes(5), secondId,
                List.of(51L, 52L), true, 1, simulationContext);

        assertRoundTrip(source, FindShipperEvent.class);
    }

    @Test
    void roundTripsShipperAcceptedEvent() throws Exception {
        var source = new ShipperAcceptedEvent(20L, 10L, 50L, "On my way", simulationContext);

        assertRoundTrip(source, ShipperAcceptedEvent.class);
    }

    @Test
    void roundTripsExpireShipperOfferCommand() throws Exception {
        var source = new ExpireShipperOfferCommand(eventId, 20L, 10L, 50L, timestamp, "session-1");

        assertRoundTrip(source, ExpireShipperOfferCommand.class);
    }

    private <T> void assertRoundTrip(T source, Class<T> type) throws Exception {
        byte[] json = mapper.writeValueAsBytes(source);

        assertThat(mapper.readValue(json, type)).isEqualTo(source);
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
