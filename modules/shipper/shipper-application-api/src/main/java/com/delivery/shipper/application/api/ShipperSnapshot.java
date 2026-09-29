package com.delivery.shipper.application.api;

import com.delivery.shipper.domain.identity.IdentityRef;

public record ShipperSnapshot(long id, IdentityRef identity, String fullName, String vehicleType,
                              String licenseNumber, String idCard, String phone, String licensePlate,
                              boolean online, int completedDeliveries, double rating, long ratingCount,
                              String identityStatus, long identityStatusVersion) { }
