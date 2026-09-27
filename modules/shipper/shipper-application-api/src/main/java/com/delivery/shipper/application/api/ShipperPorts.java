package com.delivery.shipper.application.api;

import com.delivery.shipper.domain.outbox.IdentityUpserted;
import com.delivery.shipper.domain.read.PageRequest;
import java.util.Optional;

public final class ShipperPorts {
    private ShipperPorts() { }
    public interface ProfileStore {
        ShipperSnapshot insert(ShipperCommands.CreateProfile command);
        Optional<ShipperSnapshot> findByPrincipalId(long principalId);
        Optional<ShipperSnapshot> findById(long shipperId);
        ShipperSnapshot update(ShipperCommands.UpdateProfile command);
        ShipperResults.SelfPage page(PageRequest request);
        boolean existsByLicenseNumber(String licenseNumber, Long excludingShipperId);
        boolean existsByIdCard(String idCard, Long excludingShipperId);
    }
    public interface RatingStore {
        ShipperResults.RatingResult add(ShipperCommands.SelfRating command);
    }
    public interface TrackingAvailability {
        void markOffline(long shipperId, long requestedAtEpochMillis);
    }
    public interface IdentityStatusStore {
        ShipperResults.IdentityStatusResult apply(ShipperCommands.IdentityStatusProjection command);
    }
    /** Transaction boundary: persist profile mutation and its identity event atomically. */
    public interface IdentityOutbox {
        boolean enqueueIfNew(IdentityUpserted event);
    }
    public interface IdentityOutboxRelay {
        int relayBatch(int maxItems);
    }
}
