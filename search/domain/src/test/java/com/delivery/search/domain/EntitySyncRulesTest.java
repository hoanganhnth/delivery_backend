package com.delivery.search.domain;

import com.delivery.search.domain.EntitySyncRules.Metadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class EntitySyncRulesTest {
    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 9, 30, 10, 0);
    private static final String REQUIRED = "stable eventId, occurredAt, entityType, action and entityId are required";

    private static Metadata metadata(UUID id, LocalDateTime time, String type, String action,
                                     String entityId, boolean payload, Long version, LocalDateTime deleted) {
        return new Metadata(id, time, type, action, entityId, payload, version, deleted);
    }

    static Stream<Arguments> invalidEnvelopes() {
        List<Arguments> cases = new ArrayList<>();
        cases.add(Arguments.of(null, REQUIRED));
        cases.add(Arguments.of(metadata(null, TIME, "RESTAURANT", "UPDATE", "1", true, null, null), REQUIRED));
        cases.add(Arguments.of(metadata(ID, null, "RESTAURANT", "UPDATE", "1", true, null, null), REQUIRED));
        for (String value : new String[]{null, "", " \t\n"}) {
            cases.add(Arguments.of(metadata(ID, TIME, value, "UPDATE", "1", true, null, null), REQUIRED));
            cases.add(Arguments.of(metadata(ID, TIME, "RESTAURANT", value, "1", true, null, null), REQUIRED));
            cases.add(Arguments.of(metadata(ID, TIME, "RESTAURANT", "UPDATE", value, true, null, null), REQUIRED));
        }
        for (String action : new String[]{"UPSERT", " UPDATE "}) {
            cases.add(Arguments.of(metadata(ID, TIME, "SHIPPER", action, "1", false, 0L, TIME.plusNanos(1)),
                    "Unsupported entity action: " + action));
        }
        for (String type : new String[]{"SHIPPER", " RESTAURANT "}) {
            cases.add(Arguments.of(metadata(ID, TIME, type, "UPDATE", "1", false, 0L, TIME.plusNanos(1)),
                    "Unsupported entity type: " + type));
        }
        for (String action : new String[]{"CREATE", "update"}) {
            cases.add(Arguments.of(metadata(ID, TIME, "DISH", action, "1", false, 0L, TIME.plusNanos(1)),
                    "payload is required for create/update"));
        }
        for (long version : new long[]{Long.MIN_VALUE, -1, 0}) {
            cases.add(Arguments.of(metadata(ID, TIME, "DISH", "DELETE", "1", false, version, TIME.plusNanos(1)),
                    "aggregateVersion must be positive"));
        }
        cases.add(Arguments.of(metadata(ID, TIME, "DISH", "delete", "1", false, 1L, TIME.plusNanos(1)),
                "deletedAt cannot be after occurredAt"));
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("invalidEnvelopes")
    void rejectsEachInvalidBoundaryWithOriginalFirstError(Metadata metadata, String message) {
        var exception = assertThrows(IllegalArgumentException.class, () -> EntitySyncRules.validate(metadata));
        assertEquals(IllegalArgumentException.class, exception.getClass());
        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void acceptsEverySupportedEntityActionAndOptionalMetadataCombination() {
        for (String type : new String[]{"RESTAURANT", "restaurant", "DiSh"}) {
            for (String action : new String[]{"CREATE", "create", "UPDATE", "uPdAtE", "DELETE", "delete"}) {
                for (Long version : new Long[]{null, 1L, Long.MAX_VALUE}) {
                    for (LocalDateTime deleted : new LocalDateTime[]{null, TIME.minusNanos(1), TIME}) {
                        assertDoesNotThrow(() -> EntitySyncRules.validate(
                                metadata(ID, TIME, type, action, " id ", true, version, deleted)));
                        if (action.equalsIgnoreCase("DELETE")) {
                            assertDoesNotThrow(() -> EntitySyncRules.validate(
                                    metadata(ID, TIME, type, action, "1", false, version, deleted)));
                        }
                    }
                }
                if (!action.equalsIgnoreCase("DELETE")) {
                    // Existing admission only compares deletedAt for DELETE.
                    assertDoesNotThrow(() -> EntitySyncRules.validate(
                            metadata(ID, TIME, type, action, "1", true, null, TIME.plusNanos(1))));
                }
            }
        }
    }

    @Test
    void labelsUseRootLocaleEvenWhenDefaultLocaleHasDifferentCaseRules() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertDoesNotThrow(() -> EntitySyncRules.validate(
                    metadata(ID, TIME, "dish", "update", "1", true, null, null)));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void aggregateVersionWinsWithoutReadingTheTimestampOrChangingItsValue() {
        for (long version : new long[]{Long.MIN_VALUE, -1, 0, 1, Long.MAX_VALUE}) {
            assertEquals(version, EntitySyncRules.projectionVersion(null, version));
            assertEquals(version, EntitySyncRules.projectionVersion(LocalDateTime.MAX, version));
        }
    }

    @Test
    void legacyVersionPreservesUtcEpochAndNanosecondBoundaries() {
        var epoch = LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC);
        assertEquals(0, EntitySyncRules.projectionVersion(epoch, null));
        assertEquals(1, EntitySyncRules.projectionVersion(epoch.plusNanos(1), null));
        assertEquals(-1, EntitySyncRules.projectionVersion(epoch.minusNanos(1), null));
        assertEquals(999_999_999, EntitySyncRules.projectionVersion(epoch.plusNanos(999_999_999), null));
        assertEquals(1_000_000_000, EntitySyncRules.projectionVersion(epoch.plusSeconds(1), null));
        assertEquals(1_000_000_001, EntitySyncRules.projectionVersion(epoch.plusSeconds(1).plusNanos(1), null));
        assertEquals(Long.MAX_VALUE, EntitySyncRules.projectionVersion(
                LocalDateTime.ofEpochSecond(9_223_372_036L, 854_775_807, ZoneOffset.UTC), null));
    }

    @Test
    void legacyOverflowRetainsMessageAndArithmeticCauseForMultiplyAndAdd() {
        for (LocalDateTime time : new LocalDateTime[]{LocalDateTime.MIN, LocalDateTime.MAX,
                LocalDateTime.ofEpochSecond(9_223_372_036L, 854_775_808, ZoneOffset.UTC)}) {
            var exception = assertThrows(IllegalArgumentException.class,
                    () -> EntitySyncRules.projectionVersion(time, null));
            assertEquals("entity-sync occurredAt cannot be represented as a version", exception.getMessage());
            assertInstanceOf(ArithmeticException.class, exception.getCause());
        }
        assertThrows(NullPointerException.class, () -> EntitySyncRules.projectionVersion(null, null));
    }
}
