package com.delivery.shipper.application;

import com.delivery.shipper.application.api.ShipperCommands;
import com.delivery.shipper.application.api.ShipperPorts;
import com.delivery.shipper.application.api.ShipperResults;
import com.delivery.shipper.application.api.ShipperSnapshot;
import com.delivery.shipper.application.api.ShipperUseCases;
import com.delivery.shipper.domain.identity.AuthorizationDecision;
import com.delivery.shipper.domain.identity.ShipperAuthorization;
import com.delivery.shipper.domain.identity.ShipperRole;
import com.delivery.shipper.domain.profile.ShipperProfileRules;
import com.delivery.shipper.domain.read.PageRequest;
import java.util.Objects;

/** Framework-free profile, availability, admin-read and identity use cases. */
public final class DefaultShipperProfileUseCases implements
        ShipperUseCases.CreateProfile, ShipperUseCases.UpdateProfile,
        ShipperUseCases.ReadSelf, ShipperUseCases.ReadById, ShipperUseCases.ReadPage,
        ShipperUseCases.SetOnlineStatus, ShipperUseCases.TrackingOffline,
        ShipperUseCases.ProjectIdentityStatus {
    private final ShipperPorts.ProfileStore profiles;
    private final ShipperPorts.TrackingAvailability tracking;
    private final ShipperPorts.IdentityStatusStore identity;

    public DefaultShipperProfileUseCases(ShipperPorts.ProfileStore profiles,
            ShipperPorts.TrackingAvailability tracking,
            ShipperPorts.IdentityStatusStore identity) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.tracking = Objects.requireNonNull(tracking, "tracking");
        this.identity = Objects.requireNonNull(identity, "identity");
    }

    @Override public ShipperResults.CreateProfileResult execute(ShipperCommands.CreateProfile c) {
        requireActor(c == null ? null : c.actor(), ShipperRole.SHIPPER);
        requireText(c.fullName(), "fullName"); requireText(c.vehicleType(), "vehicleType");
        requireText(c.licenseNumber(), "licenseNumber"); requireText(c.idCard(), "idCard");
        ShipperProfileRules.requireUniqueDocuments(
                profiles.existsByLicenseNumber(c.licenseNumber(), null), profiles.existsByIdCard(c.idCard(), null));
        if (profiles.findByPrincipalId(c.actor().principalId()).isPresent())
            throw new IllegalArgumentException("principal already owns a shipper profile");
        return new ShipperResults.CreateProfileResult(profiles.insert(c));
    }

    @Override public ShipperSnapshot execute(ShipperCommands.UpdateProfile c) {
        requireActor(c == null ? null : c.actor(), ShipperRole.SHIPPER);
        ShipperSnapshot current = ownedProfile(c.actor(), c.shipperId());
        ShipperProfileRules.requireUniqueDocuments(
                c.licenseNumber() != null && profiles.existsByLicenseNumber(c.licenseNumber(), c.shipperId()),
                c.idCard() != null && profiles.existsByIdCard(c.idCard(), c.shipperId()));
        return profiles.update(c);
    }

    @Override public ShipperSnapshot execute(ShipperCommands.Actor actor) {
        requireActor(actor, ShipperRole.SHIPPER);
        return profiles.findByPrincipalId(actor.principalId())
                .orElseThrow(() -> new IllegalArgumentException("shipper profile not found"));
    }

    @Override public ShipperSnapshot execute(ShipperCommands.Actor actor, long shipperId) {
        requireActor(actor, ShipperRole.ADMIN);
        return profiles.findById(positive(shipperId, "shipperId"))
                .orElseThrow(() -> new IllegalArgumentException("shipper profile not found"));
    }

    @Override public ShipperResults.SelfPage execute(ShipperCommands.Actor actor, PageRequest request) {
        requireActor(actor, ShipperRole.ADMIN);
        return profiles.page(Objects.requireNonNull(request, "request"));
    }

    @Override public ShipperSnapshot execute(ShipperCommands.SetOnlineStatus c) {
        requireActor(c == null ? null : c.actor(), ShipperRole.SHIPPER);
        ShipperSnapshot current = profiles.findByPrincipalId(c.actor().principalId())
                .orElseThrow(() -> new IllegalArgumentException("shipper profile not found"));
        if (!c.online()) tracking.markOffline(current.id(), System.currentTimeMillis());
        // Tracking must complete before this profile projection is changed.
        return profiles.updateOnline(current.id(), c.online());
    }

    @Override public void execute(ShipperCommands.TrackingOffline c) {
        Objects.requireNonNull(c, "command");
        tracking.markOffline(positive(c.shipperId(), "shipperId"), c.requestedAtEpochMillis());
    }

    @Override public ShipperResults.IdentityStatusResult execute(ShipperCommands.IdentityStatusProjection c) {
        Objects.requireNonNull(c, "command");
        if (c.principalId() <= 0 || c.version() < 1 || c.status() == null || c.status().isBlank())
            throw new IllegalArgumentException("invalid identity status projection");
        var profile = profiles.findByPrincipalId(c.principalId());
        boolean applied = false;
        if (profile.isPresent()) {
            var current = profile.get();
            if (current.identityStatusVersion() > 0 && c.version() > current.identityStatusVersion() + 1)
                throw new IllegalStateException("Identity lifecycle version gap");
            if (c.version() > current.identityStatusVersion()) {
                if ("BLOCKED".equals(c.status()) && current.online()) {
                    tracking.markOffline(current.id(), System.currentTimeMillis());
                    profiles.updateOnline(current.id(), false);
                }
                applied = identity.apply(c).applied();
            }
        }
        return new ShipperResults.IdentityStatusResult(c.principalId(), c.status(), c.version(), applied);
    }

    private ShipperSnapshot ownedProfile(ShipperCommands.Actor actor, long id) {
        ShipperSnapshot profile = profiles.findById(positive(id, "shipperId"))
                .orElseThrow(() -> new IllegalArgumentException("shipper profile not found"));
        if (ShipperAuthorization.readSelf(actor.role(), profile.identity(), actor.principalId(), actor.legacyUserId())
                != AuthorizationDecision.ALLOW)
            throw new IllegalArgumentException("shipper profile is not owned by actor");
        return profile;
    }

    private static void requireActor(ShipperCommands.Actor actor, ShipperRole role) {
        if (actor == null || actor.principalId() <= 0 || actor.role() != role)
            throw new IllegalArgumentException("actor is not authorized");
        if (actor.legacyUserId() != null && actor.legacyUserId() <= 0)
            throw new IllegalArgumentException("legacyUserId must be positive");
    }
    private static long positive(long value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive"); return value;
    }
    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
