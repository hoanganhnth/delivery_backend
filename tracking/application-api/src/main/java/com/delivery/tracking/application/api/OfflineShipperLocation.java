package com.delivery.tracking.application.api;

import java.time.Instant;

public record OfflineShipperLocation(CachedShipperLocation facts, Instant timestamp) {}
