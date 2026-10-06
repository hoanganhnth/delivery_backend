package com.delivery.promotion.domain;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static com.delivery.promotion.domain.OrderReservationEventPolicy.*;

class OrderReservationEventPolicyTest {
    @Test void absentIdentityAndCommitDoNotInspectPreviousStatus() {
        UUID id = UUID.randomUUID();
        assertThat(operation("RELEASE", null, null, null)).isEqualTo(Operation.NONE);
        assertThat(operation("COMMIT", id, null, null)).isEqualTo(Operation.COMMIT_LEGACY);
        assertThat(operation("COMMIT", null, id, null)).isEqualTo(Operation.COMMIT_BULK);
        assertThatNullPointerException().isThrownBy(() -> operation("RELEASE", id, null, null));
        assertThat(replayFailure(new Receipt("x", null, null, null, null),
                new Receipt("c", "COMMIT", 1L, null, "hash")))
                .isEqualTo("eventId replay has a contradictory voucher reservation payload");
    }

    @Test void topicsRetainRetryCanonicalizationConfiguredNamesAndPrecedence() {
        for (String input : new String[]{null, "", " "}) assertThat(topic(input, "c", "x", "r").failure()).isEqualTo("source topic is required");
        for (String suffix : new String[]{"", "-retry-promotion-0", "-retry-promotion-123"}) {
            assertThat(topic("c" + suffix, "c", "x", "r")).isEqualTo(new Topic("c", "COMMIT", null));
            for (String release : new String[]{"x", "r"}) assertThat(topic(release + suffix, "c", "x", "r")).isEqualTo(new Topic(release, "RELEASE", null));
        }
        for (String other : new String[]{"other", "c-retry-promotion-x", " c"}) assertThat(topic(other, "c", "x", "r").failure()).isEqualTo("Unexpected voucher reservation source topic: " + other);
        assertThat(topic("same", "same", "same", "same").action()).isEqualTo("COMMIT");
    }
    @Test void exhaustiveRoutingMatrixRetainsBulkPrecedenceAndUntrimmedPickupFence() {
        UUID id = UUID.randomUUID();
        for (UUID legacy : new UUID[]{null, id}) for (UUID bulk : new UUID[]{null, id}) {
            for (String action : new String[]{"COMMIT", "RELEASE"}) for (String status : new String[]{"", "CREATED", "picked_up", "DELIVERING", "DELIVERED", "COMPLETED", " PICKED_UP"}) {
                boolean after = java.util.Set.of("PICKED_UP", "DELIVERING", "DELIVERED", "COMPLETED").contains(status.toUpperCase(java.util.Locale.ROOT));
                Operation expected = legacy == null && bulk == null ? Operation.NONE : action.equals("COMMIT")
                        ? bulk != null ? Operation.COMMIT_BULK : Operation.COMMIT_LEGACY
                        : after ? Operation.NONE : bulk != null ? Operation.RELEASE_BULK : Operation.RELEASE_LEGACY;
                assertThat(operation(action, legacy, bulk, status)).isEqualTo(expected);
            }
        }
        for (boolean bulk : new boolean[]{false, true}) for (String state : new String[]{null, "COMMITTED", "RESERVED", "EXPIRED"}) {
            assertThat(commitFailure(state, bulk)).isEqualTo("COMMITTED".equals(state) ? null
                    : (bulk ? "Promotion" : "Voucher") + " reservation did not reach COMMITTED state");
        }
    }
    @Test void receiptChecksEveryFieldIncludingNullIdentityAndRawPayloadFingerprint() {
        UUID id = UUID.randomUUID();
        for (UUID identity : new UUID[]{null, id}) {
            Receipt original = new Receipt("c", "COMMIT", 1L, identity, "hash");
            assertThat(replayFailure(original, original)).isNull();
            for (Receipt changed : new Receipt[]{new Receipt("x", "COMMIT", 1L, identity, "hash"),
                    new Receipt("c", "RELEASE", 1L, identity, "hash"), new Receipt("c", "COMMIT", 2L, identity, "hash"),
                    new Receipt("c", "COMMIT", 1L, UUID.randomUUID(), "hash"), new Receipt("c", "COMMIT", 1L, identity, "hash2")}) {
                assertThat(replayFailure(original, changed)).isEqualTo("eventId replay has a contradictory voucher reservation payload");
            }
        }
    }
}
