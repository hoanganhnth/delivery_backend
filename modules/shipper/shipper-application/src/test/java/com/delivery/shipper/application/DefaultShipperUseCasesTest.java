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
    private static final ShipperSnapshot PROFILE = new ShipperSnapshot(3, new IdentityRef(7, 70L), "A", "BIKE", "L", "I", null, null, true, 0, 4.25, 2, "ACTIVE", 1);

    @Test void createEnforcesRoleAndUniqueDocuments() {
        Fakes f = new Fakes();
        var useCases = f.profileCases();
        f.principalExisting = false;
        var command = new ShipperCommands.CreateProfile(OWNER, "A", "BIKE", "L", "I", null, null);
        assertEquals(PROFILE, useCases.execute(command).profile());
        f.licenseTaken = true;
        assertThrows(IllegalArgumentException.class, () -> useCases.execute(command));
        assertThrows(IllegalArgumentException.class, () -> useCases.execute(new ShipperCommands.CreateProfile(
                new ShipperCommands.Actor(7, 70L, ShipperRole.ADMIN), "A", "BIKE", "L2", "I2", null, null)));
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
        var update = new ShipperCommands.UpdateProfile(OWNER, 3, "B", "BIKE", "L", "I", null, null);
        assertEquals(PROFILE, profileCases.execute(update));
        var ratings = new DefaultShipperRatingUseCases(f, f);
        var result = ratings.execute(new ShipperCommands.SelfRating(OWNER, 3, 99, 5, "ok"));
        assertEquals(4.3, result.average());
        f.ratingDuplicate = true;
        assertThrows(IllegalArgumentException.class, () -> ratings.execute(new ShipperCommands.SelfRating(OWNER, 3, 99, 5, "ok")));
        assertThrows(IllegalArgumentException.class, () -> ratings.execute(new ShipperCommands.SelfRating(
                new ShipperCommands.Actor(8, null, ShipperRole.SHIPPER), 3, 100, 5, null)));
    }

    static final class Fakes implements ShipperPorts.ProfileStore, ShipperPorts.TrackingAvailability,
            ShipperPorts.IdentityStatusStore, ShipperPorts.IdentityReceiptStore, ShipperPorts.RatingStore {
        boolean licenseTaken, trackingFails, ratingDuplicate, principalExisting = true; List<String> order = new ArrayList<>();
        DefaultShipperProfileUseCases profileCases() { return new DefaultShipperProfileUseCases(this, this, this, this); }
        public ShipperSnapshot insert(ShipperCommands.CreateProfile c) { return PROFILE; }
        public Optional<ShipperSnapshot> findByPrincipalId(long id) { return id == 7 && principalExisting ? Optional.of(PROFILE) : Optional.empty(); }
        public Optional<ShipperSnapshot> findById(long id) { return id == 3 ? Optional.of(PROFILE) : Optional.empty(); }
        public ShipperSnapshot update(ShipperCommands.UpdateProfile c) { order.add("update"); return PROFILE; }
        public ShipperSnapshot updateOnline(long id, boolean online) { order.add("update"); return PROFILE; }
        public ShipperResults.SelfPage page(PageRequest r) { return new ShipperResults.SelfPage(new PageSlice<>(List.of(PROFILE), r, 1)); }
        public boolean existsByLicenseNumber(String value, Long excluded) { return licenseTaken; }
        public boolean existsByIdCard(String value, Long excluded) { return false; }
        public void markOffline(long id, long at) { order.add("tracking"); if (trackingFails) throw new IllegalStateException("tracking"); }
        public ShipperResults.IdentityStatusResult apply(ShipperCommands.IdentityStatusProjection c) { return new ShipperResults.IdentityStatusResult(c.principalId(), c.status(), c.version(), true); }
        public boolean alreadyProcessed(ShipperCommands.IdentityStatusProjection c) { return false; }
        public void record(ShipperCommands.IdentityStatusProjection c) { }
        public ShipperResults.RatingResult add(ShipperCommands.SelfRating c) { return new ShipperResults.RatingResult(3, 4.25, 3); }
        public List<ShipperResults.RatingItem> findByShipperId(long id, PageRequest r) { return List.of(); }
        public boolean existsByOrderId(long id) { return ratingDuplicate; }
    }
}
