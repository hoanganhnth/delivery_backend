package com.delivery.shipper.domain;

import com.delivery.shipper.domain.identity.*;
import com.delivery.shipper.domain.outbox.IdentityUpserted;
import com.delivery.shipper.domain.profile.*;
import com.delivery.shipper.domain.rating.ShipperRating;
import com.delivery.shipper.domain.read.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ShipperDomainCoverageTest {
    private static final IdentityRef IDENTITY = new IdentityRef(7, 70L);

    @Test
    void validatesIdentityAndOutboxFacts() {
        assertThrows(IllegalArgumentException.class, () -> new IdentityRef(0, null));
        assertThrows(IllegalArgumentException.class, () -> new IdentityRef(7, 0L));
        assertThrows(IllegalArgumentException.class, () -> new IdentityUpserted(null, 7, 70L, 3, 1, Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> new IdentityUpserted(UUID.randomUUID(), 0, 70L, 3, 1, Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> new IdentityUpserted(UUID.randomUUID(), 7, 0L, 3, 1, Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> new IdentityUpserted(UUID.randomUUID(), 7, null, 0, 1, Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> new IdentityUpserted(UUID.randomUUID(), 7, null, 3, 0, Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> new IdentityUpserted(UUID.randomUUID(), 7, null, 3, 1, null));
        assertDoesNotThrow(() -> new IdentityUpserted(UUID.randomUUID(), 7, null, 3, 1, Instant.now()));
        assertTrue(IDENTITY.owns(7, null));
        assertTrue(IDENTITY.owns(0, 70L));
        assertFalse(IDENTITY.owns(8, 70L));
        assertFalse(IDENTITY.owns(0, 71L));
    }

    @Test
    void validatesRatingsAndProfileBoundaries() {
        assertThrows(IllegalArgumentException.class, () -> new ShipperRating(0, 2, 3, 5, null));
        assertThrows(IllegalArgumentException.class, () -> new ShipperRating(1, 0, 3, 5, null));
        assertThrows(IllegalArgumentException.class, () -> new ShipperRating(1, 2, 0, 5, null));
        assertThrows(IllegalArgumentException.class, () -> new ShipperRating(1, 2, 3, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new ShipperRating(1, 2, 3, 5, "x".repeat(2001)));
        assertThrows(IllegalArgumentException.class, () -> profile(0, "A", "BIKE", "L", "I", null, null, false, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> profile(3, "", "BIKE", "L", "I", null, null, false, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> profile(3, "A", "", "L", "I", null, null, false, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> profile(3, "A", "BIKE", "", "I", null, null, false, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> profile(3, "A", "BIKE", "L", "", null, null, false, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> profile(3, "A", "BIKE", "L", "I", "x".repeat(16), null, false, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> profile(3, "A", "BIKE", "L", "I", null, "x".repeat(21), false, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> profile(3, "A", "BIKE", "L", "I", null, null, false, -1, 0));
        ShipperProfile profile = profile(3, "A", "BIKE", "L", "I", null, null, false, 0, 0);
        assertEquals(0, profile.withOnline(false).version());
        assertEquals(1, profile.withOnline(true).version());
    }

    @Test
    void validatesStatusPaginationAndAuthorization() {
        assertThrows(IllegalArgumentException.class, () -> new IdentityStatusProjection(0, "ACTIVE", 1));
        assertThrows(IllegalArgumentException.class, () -> new IdentityStatusProjection(7, "ACTIVE", -1));
        assertThrows(IllegalArgumentException.class, () -> new IdentityStatusProjection(7, " ", 1));
        IdentityStatusProjection status = new IdentityStatusProjection(7, "ACTIVE", 2);
        assertThrows(NullPointerException.class, () -> status.apply(null, 3));
        assertSame(status, status.apply("ACTIVE", 2));
        assertSame(status, status.apply("ACTIVE", 1));
        assertThrows(IllegalArgumentException.class, () -> status.apply("BLOCKED", 2));
        assertEquals("BLOCKED", status.apply("BLOCKED", 3).status());
        assertEquals(AuthorizationDecision.DENY, ShipperAuthorization.readSelf(ShipperRole.ADMIN, IDENTITY, 7, 70L));
        assertEquals(AuthorizationDecision.DENY, ShipperAuthorization.readSelf(ShipperRole.SHIPPER, IDENTITY, 8, 70L));
        assertEquals(AuthorizationDecision.DENY, ShipperAuthorization.readById(ShipperRole.OTHER));
        assertThrows(IllegalArgumentException.class, () -> new PageRequest(0, 0, null, null));
        PageRequest request = new PageRequest(1, 2, true, "A");
        assertThrows(IllegalArgumentException.class, () -> new PageSlice<>(List.of(1, 2, 3), request, 4));
        PageSlice<Integer> slice = new PageSlice<>(List.of(1), request, 5);
        assertEquals(3, slice.totalPages());
        assertTrue(slice.hasNext());
        assertThrows(IllegalArgumentException.class, () -> new PageSlice<>(null, request, 0));
        assertThrows(IllegalArgumentException.class, () -> new PageSlice<>(List.of(1), request, -1));
        assertThrows(IllegalArgumentException.class, () -> OnlineTransition.decide(null, OnlineStatus.ONLINE));
        assertThrows(IllegalArgumentException.class, () -> OnlineTransition.decide(OnlineStatus.ONLINE, null));
        ShipperProfileRules.requireUniqueDocuments(false, false);
        assertThrows(IllegalArgumentException.class, () -> ShipperProfileRules.requireUniqueDocuments(false, true));
    }

    private static ShipperProfile profile(long id, String name, String vehicle, String license, String card,
            String phone, String plate, boolean online, int completed, long version) {
        return new ShipperProfile(id, IDENTITY, name, vehicle, license, card, phone, plate, online, completed, version);
    }
}
