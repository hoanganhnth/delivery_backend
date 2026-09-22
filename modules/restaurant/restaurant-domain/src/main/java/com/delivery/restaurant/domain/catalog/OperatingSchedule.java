package com.delivery.restaurant.domain.catalog;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

public final class OperatingSchedule {

    private final LocalTime open;
    private final LocalTime close;
    private final ZoneId zoneId;
    private final boolean alwaysOpen;

    private OperatingSchedule(LocalTime open, LocalTime close, ZoneId zoneId) {
        this.open = open;
        this.close = close;
        this.zoneId = zoneId;
        this.alwaysOpen = open == null || open.equals(close);
    }

    public static OperatingSchedule of(LocalTime open, LocalTime close, ZoneId zoneId) {
        if (zoneId == null) {
            throw new CatalogDomainException(
                    CatalogRuleViolation.INVALID_OPERATING_HOURS,
                    "Restaurant timezone is required");
        }
        if ((open == null) != (close == null)) {
            throw new CatalogDomainException(
                    CatalogRuleViolation.INVALID_OPERATING_HOURS,
                    "Opening and closing times must both be present or both be absent");
        }
        return new OperatingSchedule(open, close, zoneId);
    }

    public boolean isOpenAt(Instant instant) {
        if (instant == null) {
            throw new CatalogDomainException(
                    CatalogRuleViolation.INSTANT_REQUIRED,
                    "Availability instant is required");
        }
        if (alwaysOpen) {
            return true;
        }

        LocalTime localTime = instant.atZone(zoneId).toLocalTime();
        if (open.isBefore(close)) {
            return !localTime.isBefore(open) && localTime.isBefore(close);
        }
        return !localTime.isBefore(open) || localTime.isBefore(close);
    }
}
