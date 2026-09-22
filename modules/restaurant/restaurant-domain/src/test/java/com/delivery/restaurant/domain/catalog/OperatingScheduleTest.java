package com.delivery.restaurant.domain.catalog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class OperatingScheduleTest {

    private static final ZoneId HO_CHI_MINH = ZoneId.of("Asia/Ho_Chi_Minh");

    @Test
    void nullPairAndEqualTimesMeanAlwaysOpen() {
        assertTrue(OperatingSchedule.of(null, null, HO_CHI_MINH)
                .isOpenAt(Instant.parse("2026-09-21T18:00:00Z")));
        assertTrue(OperatingSchedule.of(LocalTime.of(8, 0), LocalTime.of(8, 0), HO_CHI_MINH)
                .isOpenAt(Instant.parse("2026-09-21T18:00:00Z")));
    }

    @Test
    void exactlyOneMissingTimeIsInvalid() {
        assertViolation(() -> OperatingSchedule.of(LocalTime.of(8, 0), null, HO_CHI_MINH));
        assertViolation(() -> OperatingSchedule.of(null, LocalTime.of(22, 0), HO_CHI_MINH));
    }

    @Test
    void sameDayWindowIncludesOpenAndExcludesClose() {
        OperatingSchedule schedule = OperatingSchedule.of(
                LocalTime.of(8, 0), LocalTime.of(22, 0), HO_CHI_MINH);

        assertTrue(schedule.isOpenAt(Instant.parse("2026-09-22T01:00:00Z")));
        assertTrue(schedule.isOpenAt(Instant.parse("2026-09-22T14:59:59Z")));
        assertFalse(schedule.isOpenAt(Instant.parse("2026-09-22T15:00:00Z")));
        assertFalse(schedule.isOpenAt(Instant.parse("2026-09-22T00:59:59Z")));
    }

    @Test
    void overnightWindowIncludesBothSidesOfMidnightAndExcludesClose() {
        OperatingSchedule schedule = OperatingSchedule.of(
                LocalTime.of(18, 0), LocalTime.of(2, 0), HO_CHI_MINH);

        assertTrue(schedule.isOpenAt(Instant.parse("2026-09-22T11:00:00Z")));
        assertTrue(schedule.isOpenAt(Instant.parse("2026-09-22T17:30:00Z")));
        assertFalse(schedule.isOpenAt(Instant.parse("2026-09-22T19:00:00Z")));
        assertFalse(schedule.isOpenAt(Instant.parse("2026-09-22T10:59:59Z")));
    }

    @Test
    void instantIsConvertedUsingRestaurantTimezone() {
        Instant instant = Instant.parse("2026-09-22T01:30:00Z");
        OperatingSchedule vietnam = OperatingSchedule.of(
                LocalTime.of(8, 0), LocalTime.of(9, 0), HO_CHI_MINH);
        OperatingSchedule utc = OperatingSchedule.of(
                LocalTime.of(8, 0), LocalTime.of(9, 0), ZoneId.of("UTC"));

        assertTrue(vietnam.isOpenAt(instant));
        assertFalse(utc.isOpenAt(instant));
    }

    @Test
    void dstOverlapMapsBothOccurrencesIntoTheSameLocalWindow() {
        OperatingSchedule schedule = OperatingSchedule.of(
                LocalTime.of(1, 0), LocalTime.of(1, 45), ZoneId.of("America/New_York"));

        assertTrue(schedule.isOpenAt(Instant.parse("2026-11-01T05:30:00Z")));
        assertTrue(schedule.isOpenAt(Instant.parse("2026-11-01T06:30:00Z")));
    }

    @Test
    void missingZoneOrInstantIsInvalid() {
        assertViolation(() -> OperatingSchedule.of(LocalTime.NOON, LocalTime.MIDNIGHT, null));
        OperatingSchedule schedule = OperatingSchedule.of(null, null, HO_CHI_MINH);
        CatalogDomainException exception = assertThrows(
                CatalogDomainException.class, () -> schedule.isOpenAt(null));
        assertEquals(CatalogRuleViolation.INSTANT_REQUIRED, exception.violation());
    }

    private static void assertViolation(org.junit.jupiter.api.function.Executable executable) {
        CatalogDomainException exception = assertThrows(CatalogDomainException.class, executable);
        assertEquals(CatalogRuleViolation.INVALID_OPERATING_HOURS, exception.violation());
    }
}
