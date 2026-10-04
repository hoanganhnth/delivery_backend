package com.delivery.tracking.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocationDeliveryOrderTest {
    @Test void latestFactWinsAndInvalidOrReplayedTimeCannotChangeTheWatermark() {
        var order = new LocationDeliveryOrder();
        assertThrows(IllegalArgumentException.class, () -> order.admit(0));
        assertThrows(IllegalArgumentException.class, () -> order.admit(-1));
        assertTrue(order.admit(10));
        assertFalse(order.admit(9)); assertFalse(order.admit(10));
        assertTrue(order.admit(11));
        assertTrue(new LocationDeliveryOrder().admit(9));
    }
}
