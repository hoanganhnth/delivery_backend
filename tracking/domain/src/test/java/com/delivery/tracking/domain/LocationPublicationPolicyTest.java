package com.delivery.tracking.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocationPublicationPolicyTest {
    @Test void socketMustReplicateBeforeFanoutWhileApplicationRetainsItsEstablishedFanoutFirstOrder() {
        assertTrue(LocationUpdateSource.WEBSOCKET.publishesBeforeFanout());
        assertFalse(LocationUpdateSource.APPLICATION.publishesBeforeFanout());
    }
}
