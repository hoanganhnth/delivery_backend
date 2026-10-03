package com.delivery.tracking.application.api;

import java.time.LocalDateTime;

public record OfflineShipperLocation(CachedShipperLocation facts, LocalDateTime timestamp) {}
