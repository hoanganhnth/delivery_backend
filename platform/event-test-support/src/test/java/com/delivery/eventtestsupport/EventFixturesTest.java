package com.delivery.eventtestsupport;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class EventFixturesTest {

    @Test
    void orderFixtureUsesVietnameseDeliveryDataAndVndAmounts() {
        TestEvents.OrderCreatedEvent event = EventFixtures.orderCreated();

        assertThat(event.restaurantName()).isEqualTo("Bếp Nhà Mình");
        assertThat(event.customerName()).isEqualTo("Nguyễn Minh Anh");
        assertThat(event.pickupLat()).isBetween(10.7, 10.9);
        assertThat(event.pickupLng()).isBetween(106.5, 106.9);
        assertThat(event.totalPrice()).isEqualByComparingTo(new BigDecimal("138000"));
        assertThat(event.totalPrice().scale()).isZero();
    }

    @Test
    void commonEventCatalogProvidesAllFixtureShapes() {
        assertThat(EventFixtures.commonEvents()).hasSize(11)
                .extracting(event -> event.getClass().getSimpleName())
                .containsExactlyInAnyOrder(
                        "OrderCreatedEvent", "OrderCancelledEvent", "DeliveryStatusUpdatedEvent",
                        "DeliveryCompletedEvent", "ShipperFoundEvent", "ShipperNotFoundEvent",
                        "ShipperLocationUpdatedEvent", "IdentityStatusChangedEvent",
                        "RestaurantOrderDecisionEvent", "PaymentEvent", "DeliveryExceptionReportedEvent");
    }

    @Test
    void factoriesUseStableEventIdsForReplayTests() {
        assertThat(EventFixtures.orderCreated()).isEqualTo(EventFixtures.orderCreated());
        assertThat(EventFixtures.deliveryCompleted()).isEqualTo(EventFixtures.deliveryCompleted());
        assertThat(EventFixtures.shipperLocationUpdated().timestamp()).isPositive();
    }
}
