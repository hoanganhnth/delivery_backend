package com.delivery.eventtestsupport;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonRoundTripAssertTest {

    @Test
    void roundTripsJavaTimeRecordAndReturnsDecodedValue() {
        TestEvents.OrderCreatedEvent event = EventFixtures.orderCreated();

        TestEvents.OrderCreatedEvent restored = JsonRoundTripAssert.assertRoundTrip(
                EventFixtures.objectMapper(), event, TestEvents.OrderCreatedEvent.class);

        assertThat(restored).isEqualTo(event);
    }

    @Test
    void supportsTypeReferencesForCollectionsOfEvents() {
        List<TestEvents.DeliveryStatusUpdatedEvent> events = List.of(EventFixtures.deliveryStatusUpdated());

        List<TestEvents.DeliveryStatusUpdatedEvent> restored = JsonRoundTripAssert.assertRoundTrip(
                EventFixtures.objectMapper(), events,
                new TypeReference<List<TestEvents.DeliveryStatusUpdatedEvent>>() { });

        assertThat(restored).containsExactlyElementsOf(events);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void reportsDeserializationFailureAsAssertionFailure() {
        assertThatThrownBy(() -> JsonRoundTripAssert.assertRoundTrip(
                EventFixtures.objectMapper(), EventFixtures.orderCreated(), (Class) TestEvents.PaymentEvent.class))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("failed JSON round trip");
    }

    @Test
    void rejectsMissingArguments() {
        assertThatThrownBy(() -> JsonRoundTripAssert.assertRoundTrip(
                EventFixtures.objectMapper(), null, TestEvents.OrderCreatedEvent.class))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
