package com.delivery.shipper.domain.profile;

import com.delivery.shipper.domain.identity.IdentityRef;
import java.util.Objects;

/** Immutable aggregate snapshot; adapters decide how it is persisted. */
public record ShipperProfile(
        long id, IdentityRef identity, String fullName, String vehicleType,
        String licenseNumber, String idCard, String phone, String licensePlate,
        boolean online, int completedDeliveries, long version) {
    public ShipperProfile {
        if (id <= 0) throw new IllegalArgumentException("shipper id must be positive");
        Objects.requireNonNull(identity, "identity");
        requireText(fullName, "fullName", 100);
        requireText(vehicleType, "vehicleType", 50);
        requireText(licenseNumber, "licenseNumber", 50);
        requireText(idCard, "idCard", 20);
        if (phone != null && phone.length() > 15) throw new IllegalArgumentException("phone exceeds 15 characters");
        if (licensePlate != null && licensePlate.length() > 20) throw new IllegalArgumentException("licensePlate exceeds 20 characters");
        if (completedDeliveries < 0 || version < 0) throw new IllegalArgumentException("counts and version cannot be negative");
    }

    public ShipperProfile withOnline(boolean requested) {
        return new ShipperProfile(id, identity, fullName, vehicleType, licenseNumber, idCard, phone, licensePlate,
                requested, completedDeliveries, version + (requested == online ? 0 : 1));
    }

    private static void requireText(String value, String name, int max) {
        if (value == null || value.isBlank() || value.length() > max) throw new IllegalArgumentException(name + " is required and bounded");
    }
}
