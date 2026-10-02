package com.delivery.restaurant.domain.ownership;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RestaurantManagementFactsTest {

    @Test
    void ownerPrincipalMustBePositiveWhenPresent() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> new RestaurantManagementFacts(0L, 7L));
        assertEquals("ownerPrincipalId must be positive", error.getMessage());
    }

    @Test
    void unmigratedRestaurantMayHaveNoOwnerPrincipal() {
        new RestaurantManagementFacts(null, 7L);
    }
}
