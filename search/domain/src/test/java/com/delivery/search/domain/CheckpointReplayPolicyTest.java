package com.delivery.search.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.delivery.search.domain.CheckpointReplayPolicy.*;

class CheckpointReplayPolicyTest {
    private final Expected expected = new Expected("event", "2026-09-30T10:00", "UPDATE");
    private Decision classify(String id, String time, String action, String fingerprint) {
        return CheckpointReplayPolicy.classify(expected, new Stored(id, time, action, fingerprint), "fp", "DISH:1");
    }
    @Test void exactReplayAndLegacyUpgradeNormalizeLocalFieldsIncludingNonUtcOffset() {
        for (String time : new String[]{"2026-09-30T10:00", "2026-09-30T10:00:00.000",
                "2026-09-30T10:00:00Z", "2026-09-30T10:00:00+07:00"}) {
            assertEquals(Decision.EXACT_REPLAY, classify("event", time, "UPDATE", "fp"));
            assertEquals(Decision.UPGRADE_LEGACY, classify("event", time, "UPDATE", null));
        }
    }
    @Test void metadataPrecedesPayloadAndLegacyUpgrade() {
        for (String fp : new String[]{null, "changed", "fp"}) {
            assertMessage(IllegalArgumentException.class, "entity-sync eventId replay has contradictory metadata for DISH:1",
                    () -> classify("event", "2026-09-30T10:00:01", "UPDATE", fp));
            for (String action : new String[]{null, "update", "DELETE"}) {
                assertMessage(IllegalArgumentException.class, "entity-sync eventId replay has contradictory metadata for DISH:1",
                        () -> classify("event", expected.occurredAt(), action, fp));
            }
        }
        assertMessage(IllegalArgumentException.class, "entity-sync eventId replay has contradictory payload for DISH:1",
                () -> classify("event", expected.occurredAt(), "UPDATE", "changed"));
    }
    @Test void differentIdentityChronologyAndMissingTime() {
        assertEquals(Decision.STALE, classify("other", "2026-09-30T10:00:00.000000001", null, null));
        assertEquals(Decision.STALE, classify(null, "2026-09-30T10:00:01Z", null, null));
        assertMessage(IllegalArgumentException.class, "Conflicting entity-sync events share the same occurredAt for DISH:1",
                () -> classify("other", "2026-09-30T10:00:00+07:00", null, null));
        assertMessage(IllegalStateException.class, "Checkpoint claim regressed for DISH:1",
                () -> classify("other", "2026-09-30T09:59:59", null, null));
        assertMessage(IllegalStateException.class, "Checkpoint has no comparable occurredAt for DISH:1",
                () -> classify("other", null, null, null));
    }
    @Test void invalidTimesPreserveCauseAndSuppressedLocalParserFailure() {
        for (String time : new String[]{null, "garbage", ""}) {
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> classify("event", time, "UPDATE", "fp"));
            assertEquals("Checkpoint has an invalid occurredAt value", failure.getMessage());
            assertNotNull(failure.getCause());
            assertEquals(1, failure.getCause().getSuppressed().length);
        }
        assertThrows(IllegalStateException.class, () -> classify("other", "bad", null, null));
        assertThrows(IllegalStateException.class, () -> CheckpointReplayPolicy.classify(
                new Expected("event", "bad", "UPDATE"), new Stored("event", expected.occurredAt(), "UPDATE", "fp"), "fp", "DISH:1"));
    }
    @Test void documentsLexicalScriptAndChronologicalNoopDisagreement() {
        String stored = "2026-09-30T10:00:00Z";
        String incoming = "2026-09-30T10:00:00.000000001";
        assertTrue(stored.compareTo(incoming) > 0); // Script noops despite newer local time.
        assertMessage(IllegalStateException.class, "Checkpoint claim regressed for DISH:1",
                () -> CheckpointReplayPolicy.classify(new Expected("new", incoming, "UPDATE"),
                        new Stored("old", stored, "UPDATE", "fp"), "next", "DISH:1"));
    }
    private static <T extends Throwable> void assertMessage(Class<T> type, String message,
                                                            org.junit.jupiter.api.function.Executable call) {
        assertEquals(message, assertThrows(type, call).getMessage());
    }
}
