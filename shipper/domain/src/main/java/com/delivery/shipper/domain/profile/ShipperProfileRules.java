package com.delivery.shipper.domain.profile;

/** Persistence-independent checks used before applying unique document changes. */
public final class ShipperProfileRules {
    private ShipperProfileRules() { }

    public static void requireUniqueDocuments(boolean licenseNumberTaken, boolean idCardTaken) {
        if (licenseNumberTaken) throw new IllegalArgumentException("licenseNumber is already owned");
        if (idCardTaken) throw new IllegalArgumentException("idCard is already owned");
    }
}
