package com.delivery.restaurant.domain.ownership;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OwnershipExceptionTest {

    @Test
    void ownerAssignmentExceptionRetainsStructuredFailure() {
        var error = new OwnerAssignmentException(OwnerAssignmentFailure.OWNER_REQUIRED);
        assertEquals(OwnerAssignmentFailure.OWNER_REQUIRED, error.failure());
    }

    @Test
    void managementAccessExceptionRetainsStructuredFailure() {
        var error = new ManagementAccessException(
                ManagementAccessFailure.ACTOR_NOT_ALLOWED);
        assertEquals(ManagementAccessFailure.ACTOR_NOT_ALLOWED, error.failure());
    }

    @Test
    void managementDecisionMarksDirectAndLegacyAccessSeparately() {
        assertEquals(false, RestaurantManagementAccessDecision.direct().usedLegacyFallback());
        assertEquals(true,
                RestaurantManagementAccessDecision.legacyFallback().usedLegacyFallback());
    }
}
