package com.delivery.shipper.domain;

import com.delivery.shipper.domain.identity.*;
import com.delivery.shipper.domain.profile.*;
import com.delivery.shipper.domain.rating.ShipperRating;
import com.delivery.shipper.domain.read.PageRequest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipperDomainTest {
    @Test void principalTakesPrecedenceOverLegacyIdentity() {
        IdentityRef ref = new IdentityRef(7, 9L);
        assertTrue(ref.owns(7, 999L));
        assertFalse(ref.owns(8, 9L));
        assertTrue(ref.owns(0, 9L));
    }
    @Test void rolesAreNarrow() {
        IdentityRef ref = new IdentityRef(7, null);
        assertEquals(AuthorizationDecision.ALLOW, ShipperAuthorization.readSelf(ShipperRole.SHIPPER, ref, 7, null));
        assertEquals(AuthorizationDecision.ALLOW, ShipperAuthorization.readById(ShipperRole.ADMIN));
        assertEquals(AuthorizationDecision.DENY, ShipperAuthorization.readById(ShipperRole.SHIPPER));
    }
    @Test void profileAndRatingRejectInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new IdentityRef(0, null));
        assertThrows(IllegalArgumentException.class, () -> new ShipperRating(1, 2, 3, 6, null));
        assertThrows(IllegalArgumentException.class, () -> ShipperProfileRules.requireUniqueDocuments(true, false));
        assertDoesNotThrow(() -> ShipperProfileRules.requireUniqueDocuments(false, false));
    }
    @Test void onlineTransitionIsIdempotentWhenStateUnchanged() {
        assertFalse(OnlineTransition.decide(OnlineStatus.OFFLINE, OnlineStatus.OFFLINE).changed());
        assertTrue(OnlineTransition.decide(OnlineStatus.OFFLINE, OnlineStatus.ONLINE).changed());
    }
    @Test void paginationIsBounded() {
        assertThrows(IllegalArgumentException.class, () -> new PageRequest(0, 101));
        assertThrows(IllegalArgumentException.class, () -> new PageRequest(-1, 10));
    }
    @Test void identityVersionsAreMonotonicAndIdempotent() {
        IdentityStatusProjection p = new IdentityStatusProjection(7, "ACTIVE", 2);
        assertSame(p, p.apply("ACTIVE", 2));
        assertSame(p, p.apply("DISABLED", 1));
        assertThrows(IllegalArgumentException.class, () -> p.apply("DISABLED", 2));
        assertEquals(3, p.apply("DISABLED", 3).version());
    }
}
