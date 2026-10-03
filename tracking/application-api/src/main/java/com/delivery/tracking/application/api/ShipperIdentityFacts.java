package com.delivery.tracking.application.api;

/** Canonical facts from Tracking's local principal-to-shipper projection. */
public record ShipperIdentityFacts(Long legacyUserId, Long shipperId) {}
