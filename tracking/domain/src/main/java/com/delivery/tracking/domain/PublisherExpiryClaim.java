package com.delivery.tracking.domain;

/** Redis claim deadline fences completion against a newer recovery attempt. */
public record PublisherExpiryClaim(PublisherLease lease, long claimUntilEpochMillis) {}
