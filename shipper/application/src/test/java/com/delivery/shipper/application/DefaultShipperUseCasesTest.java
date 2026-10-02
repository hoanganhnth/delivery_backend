package com.delivery.shipper.application;

import com.delivery.shipper.application.api.*;
import com.delivery.shipper.domain.identity.IdentityRef;
import com.delivery.shipper.domain.identity.ShipperRole;
import com.delivery.shipper.domain.read.PageRequest;
import com.delivery.shipper.domain.read.PageSlice;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefaultShipperUseCasesTest {
    private static final ShipperCommands.Actor OWNER = new ShipperCommands.Actor(7, 70L, ShipperRole.SHIPPER);
    private static final ShipperSnapshot PROFILE = new ShipperSnapshot(3, new IdentityRef(7, 70L), "A", "BIKE", "L", "I", null, null, true, 0, 4.25, 2, "ACTIVE", 1, null, null, null, null, null, null);

    @Test void identityStatusGapIsRejectedBeforeRecordingReceipt() {
        Fakes f = new Fakes();
        assertThrows(IllegalStateException.class, () -> f.profileCases().execute(
                new ShipperCommands.IdentityStatusProjection(7, "BLOCKED", 3)));
    }

    @Test void blockedIdentityConvergesTrackingBeforeLocalOfflineProjection() {
        Fakes f = new Fakes();
        f.profileCases().execute(new ShipperCommands.IdentityStatusProjection(7, "BLOCKED", 2));
        assertEquals(List.of("tracking", "update"), f.order);
    }

    @Test void createEnforcesRoleAndUniqueDocuments() {
        Fakes f = new Fakes();
        var useCases = f.profileCases();
        f.principalExisting = false;
        var command = new ShipperCommands.CreateProfile(OWNER, "A", "BIKE", "L", "I", null, null, null, null, null, null);
        assertEquals(PROFILE, useCases.execute(command).profile());
        f.licenseTaken = true;
        assertThrows(IllegalArgumentException.class, () -> useCases.execute(command));
        assertThrows(IllegalArgumentException.class, () -> useCases.execute(new ShipperCommands.CreateProfile(
                new ShipperCommands.Actor(7, 70L, ShipperRole.ADMIN), "A", "BIKE", "L2", "I2", null, null, null, null, null, null)));
    }

    @Test void offlineTracksBeforeProjectionAndFailureLeavesStateUntouched() {
        Fakes f = new Fakes();
        var useCases = f.profileCases();
        useCases.execute(new ShipperCommands.SetOnlineStatus(OWNER, false));
        assertEquals(List.of("tracking", "update"), f.order);
        f.order.clear(); f.trackingFails = true;
        assertThrows(IllegalStateException.class, () -> useCases.execute(new ShipperCommands.SetOnlineStatus(OWNER, false)));
        assertEquals(List.of("tracking"), f.order);
    }

    @Test void profileUpdateIsSelfOnlyAndRatingIsSelfOwnedAndRounded() {
        Fakes f = new Fakes();
        var profileCases = f.profileCases();
        var update = new ShipperCommands.UpdateProfile(OWNER, 3, "B", "BIKE", "L", "I", null, null, null, null, null, null);
        assertEquals(PROFILE, profileCases.execute(update));
        var ratings = new DefaultShipperRatingUseCases(f, f);
        var result = ratings.execute(new ShipperCommands.SelfRating(OWNER, 3, 99, 5, "ok"));
        assertEquals(4.3, result.average());
        f.ratingDuplicate = true;
        assertThrows(IllegalArgumentException.class, () -> ratings.execute(new ShipperCommands.SelfRating(OWNER, 3, 99, 5, "ok")));
        assertThrows(IllegalArgumentException.class, () -> ratings.execute(new ShipperCommands.SelfRating(
                new ShipperCommands.Actor(8, null, ShipperRole.SHIPPER), 3, 100, 5, null)));
    }

    @Test void readsAdminPagesAndSelfRatings() {
        Fakes f = new Fakes();
        var profiles = f.profileCases();
        assertEquals(PROFILE, profiles.execute(OWNER));
        assertEquals(PROFILE, profiles.execute(new ShipperCommands.Actor(1, null, ShipperRole.ADMIN), 3));
        assertEquals(1, profiles.execute(new ShipperCommands.Actor(1, null, ShipperRole.ADMIN), new PageRequest(0, 10, null, null)).page().totalItems());
        var ratings = new DefaultShipperRatingUseCases(f, f);
        assertEquals(0, ratings.execute(OWNER).size());
        f.principalExisting = false;
        assertThrows(IllegalArgumentException.class, () -> profiles.execute(OWNER));
        assertThrows(IllegalArgumentException.class, () -> ratings.execute(OWNER));
    }

    @Test void coversStatusProjectionAndOnlineTransitions() {
        Fakes f = new Fakes();
        var profiles = f.profileCases();
        assertEquals(PROFILE, profiles.execute(new ShipperCommands.SetOnlineStatus(OWNER, true)));
        profiles.execute(new ShipperCommands.TrackingOffline(3, 10));
        assertThrows(IllegalArgumentException.class, () -> profiles.execute(new ShipperCommands.TrackingOffline(0, 10)));
        assertEquals(1, profiles.execute(new ShipperCommands.IdentityStatusProjection(7, "ACTIVE", 1)).version());
        assertFalse(profiles.execute(new ShipperCommands.IdentityStatusProjection(7, "ACTIVE", 1)).applied());
        assertThrows(IllegalArgumentException.class, () -> profiles.execute(new ShipperCommands.IdentityStatusProjection(0, "ACTIVE", 1)));
        assertThrows(IllegalArgumentException.class, () -> profiles.execute(new ShipperCommands.IdentityStatusProjection(7, " ", 1)));
    }

    @Test void rejectsInvalidProfileAndRatingCommands() {
        Fakes f = new Fakes();
        var profiles = f.profileCases();
        assertThrows(IllegalArgumentException.class, () -> profiles.execute((ShipperCommands.CreateProfile) null));
        assertThrows(IllegalArgumentException.class, () -> profiles.execute(new ShipperCommands.CreateProfile(
                OWNER, "", "BIKE", "L", "I", null, null, null, null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> profiles.execute(new ShipperCommands.CreateProfile(
                new ShipperCommands.Actor(0, null, ShipperRole.SHIPPER), "A", "BIKE", "L", "I", null, null, null, null, null, null)));
        f.cardTaken = true;
        assertThrows(IllegalArgumentException.class, () -> profiles.execute(new ShipperCommands.CreateProfile(
                OWNER, "A", "BIKE", "L", "I", null, null, null, null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> profiles.execute(new ShipperCommands.UpdateProfile(OWNER, 0, "A", "BIKE", "L", "I", null, null, null, null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> profiles.execute(new ShipperCommands.UpdateProfile(
                new ShipperCommands.Actor(1, null, ShipperRole.ADMIN), 3, "A", "BIKE", "L", "I", null, null, null, null, null, null)));
        var ratings = new DefaultShipperRatingUseCases(f, f);
        assertThrows(IllegalArgumentException.class, () -> ratings.execute((ShipperCommands.SelfRating) null));
        assertThrows(IllegalArgumentException.class, () -> ratings.execute(new ShipperCommands.SelfRating(OWNER, 0, 99, 5, null)));
        assertThrows(IllegalArgumentException.class, () -> ratings.execute(new ShipperCommands.SelfRating(OWNER, 3, 0, 5, null)));
        assertThrows(IllegalArgumentException.class, () -> ratings.execute(new ShipperCommands.SelfRating(OWNER, 3, 99, 0, null)));
        assertThrows(IllegalArgumentException.class, () -> ratings.execute(new ShipperCommands.SelfRating(OWNER, 3, 99, 5, "x".repeat(2001))));
        assertThrows(IllegalArgumentException.class, () -> ratings.execute(new ShipperCommands.Actor(1, null, ShipperRole.ADMIN)));
    }

    @Test void coversRemainingAuthorizationAndProviderBranches() {
        Fakes f = new Fakes();
        var profiles = f.profileCases();
        assertThrows(IllegalArgumentException.class, () -> profiles.execute(new ShipperCommands.CreateProfile(
                OWNER, "A", "BIKE", "L", "I", null, null, null, null, null, null)));
        f.principalExisting = false;
        assertEquals(PROFILE, profiles.execute(new ShipperCommands.UpdateProfile(OWNER, 3, "A", "BIKE", null, null, null, null, null, null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> profiles.execute(new ShipperCommands.Actor(1, null, ShipperRole.ADMIN), 99));
        assertThrows(NullPointerException.class, () -> profiles.execute(new ShipperCommands.Actor(1, null, ShipperRole.ADMIN), (PageRequest) null));
        f.principalExisting = false;
        assertThrows(IllegalArgumentException.class, () -> profiles.execute(new ShipperCommands.SetOnlineStatus(OWNER, true)));
        assertThrows(IllegalArgumentException.class, () -> profiles.execute(new ShipperCommands.IdentityStatusProjection(7, "ACTIVE", 0)));
        assertThrows(IllegalArgumentException.class, () -> profiles.execute(new ShipperCommands.IdentityStatusProjection(7, null, 1)));
        assertThrows(IllegalArgumentException.class, () -> profiles.execute(new ShipperCommands.Actor(7, 0L, ShipperRole.SHIPPER)));
        assertThrows(NullPointerException.class, () -> profiles.execute((ShipperCommands.TrackingOffline) null));
        var ratings = new DefaultShipperRatingUseCases(f, f);
        f.principalExisting = false;
        f.profileMissing = true;
        assertThrows(IllegalArgumentException.class, () -> ratings.execute(new ShipperCommands.SelfRating(OWNER, 3, 99, 5, null)));
        f.profileMissing = false;
        f.principalExisting = true;
        f.ratingDuplicate = false;
        assertThrows(IllegalArgumentException.class, () -> ratings.execute(new ShipperCommands.SelfRating(OWNER, 3, 99, 6, null)));
        assertEquals(4.3, ratings.execute(new ShipperCommands.SelfRating(OWNER, 3, 99, 5, null)).average());
        assertThrows(IllegalArgumentException.class, () -> ratings.execute(new ShipperCommands.SelfRating(
                new ShipperCommands.Actor(8, null, ShipperRole.SHIPPER), 3, 99, 5, null)));

        f.licenseTaken = true;
        assertThrows(IllegalArgumentException.class, () -> profiles.execute(new ShipperCommands.UpdateProfile(
                OWNER, 3, "A", "BIKE", "L", "I", null, null, null, null, null, null)));
        f.licenseTaken = false;
        f.cardTaken = true;
        assertThrows(IllegalArgumentException.class, () -> profiles.execute(new ShipperCommands.UpdateProfile(
                OWNER, 3, "A", "BIKE", "L", "I", null, null, null, null, null, null)));
    }

    static final class Fakes implements ShipperPorts.ProfileStore, ShipperPorts.TrackingAvailability,
            ShipperPorts.IdentityStatusStore, ShipperPorts.RatingStore {
        boolean licenseTaken, cardTaken, trackingFails, ratingDuplicate, profileMissing, principalExisting = true; List<String> order = new ArrayList<>();
        DefaultShipperProfileUseCases profileCases() { return new DefaultShipperProfileUseCases(this, this, this); }
        public ShipperSnapshot insert(ShipperCommands.CreateProfile c) { return PROFILE; }
        public Optional<ShipperSnapshot> findByPrincipalId(long id) { return id == 7 && principalExisting ? Optional.of(PROFILE) : Optional.empty(); }
        public Optional<ShipperSnapshot> findById(long id) { return id == 3 && !profileMissing ? Optional.of(PROFILE) : Optional.empty(); }
        public ShipperSnapshot update(ShipperCommands.UpdateProfile c) { order.add("update"); return PROFILE; }
        public ShipperSnapshot updateOnline(long id, boolean online) { order.add("update"); return PROFILE; }
        public ShipperResults.SelfPage page(PageRequest r) { return new ShipperResults.SelfPage(new PageSlice<>(List.of(PROFILE), r, 1)); }
        public boolean existsByLicenseNumber(String value, Long excluded) { return licenseTaken; }
        public boolean existsByIdCard(String value, Long excluded) { return cardTaken; }
        public void markOffline(long id, long at) { order.add("tracking"); if (trackingFails) throw new IllegalStateException("tracking"); }
        public ShipperResults.IdentityStatusResult apply(ShipperCommands.IdentityStatusProjection c) { return new ShipperResults.IdentityStatusResult(c.principalId(), c.status(), c.version(), true); }
        public ShipperResults.RatingResult add(ShipperCommands.SelfRating c) { return new ShipperResults.RatingResult(3, 4.25, 3); }
        public List<ShipperResults.RatingItem> findByShipperId(long id, PageRequest r) { return List.of(); }
        public boolean existsByOrderId(long id) { return ratingDuplicate; }
    }
}
