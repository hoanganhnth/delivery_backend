package com.delivery.restaurant.domain.ownership;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PrincipalOwnershipFactsTest {

    @Test
    void preservesOnlyTheOwnershipFactsRestaurantNeeds() {
        var facts = new PrincipalOwnershipFacts(42L, true, true);

        assertEquals(42L, facts.principalId());
        assertEquals(true, facts.shopOwner());
        assertEquals(true, facts.active());
        assertArrayEquals(
                new RestaurantActorRole[] {
                    RestaurantActorRole.ADMIN,
                    RestaurantActorRole.SHOP_OWNER,
                    RestaurantActorRole.OTHER
                },
                RestaurantActorRole.values());
    }

    @Test
    void rejectsNonPositivePrincipalIdentity() {
        assertThrows(IllegalArgumentException.class, () ->
                new PrincipalOwnershipFacts(0L, true, true));
    }
}
