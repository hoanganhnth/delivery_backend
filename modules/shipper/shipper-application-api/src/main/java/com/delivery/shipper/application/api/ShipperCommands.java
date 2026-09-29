package com.delivery.shipper.application.api;

import com.delivery.shipper.domain.identity.ShipperRole;

public interface ShipperCommands {
    public record Actor(long principalId, Long legacyUserId, ShipperRole role) { }
    public record CreateProfile(Actor actor, String fullName, String vehicleType, String licenseNumber, String idCard,
                                String phone, String licensePlate) { }
    public record UpdateProfile(Actor actor, long shipperId, String fullName, String vehicleType, String licenseNumber,
                                String idCard, String phone, String licensePlate) { }
    public record SetOnlineStatus(Actor actor, boolean online) { }
    public record SelfRating(Actor actor, long shipperId, long orderId, int score, String comment) { }
    public record TrackingOffline(long shipperId, long requestedAtEpochMillis) { }
    public record IdentityStatusProjection(long principalId, String status, long version) { }
}
