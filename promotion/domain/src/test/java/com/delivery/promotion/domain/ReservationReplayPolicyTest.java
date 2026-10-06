package com.delivery.promotion.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ReservationReplayPolicyTest {
    private final UUID id = UUID.randomUUID();
    @Test void bindingPreservesOrderAndPrincipalContractsIncludingMalformedRows() {
        for (boolean bulk : new boolean[]{false, true}) {
            assertThat(ReservationReplayPolicy.orderFailure(1L, 1L, bulk)).isNull();
            assertThat(ReservationReplayPolicy.orderFailure(1L, 2L, bulk))
                    .isEqualTo("reservationId is bound to another order");
        }
        assertThat(ReservationReplayPolicy.orderFailure(null, 1L, true))
                .isEqualTo("reservationId is bound to another order");
        assertThatNullPointerException().isThrownBy(() -> ReservationReplayPolicy.orderFailure(null, 1L, false));
        assertThatNullPointerException().isThrownBy(() -> ReservationReplayPolicy.orderFailure(1L, null, true));
        assertThat(ReservationReplayPolicy.orderFailure(1L, null, false))
                .isEqualTo("reservationId is bound to another order");
        for (Long stored : new Long[]{null, 1L, 2L}) for (Long requested : new Long[]{null, 1L, 2L}) {
            assertThat(ReservationReplayPolicy.principalFailure(stored, requested))
                    .isEqualTo(stored != null && stored.equals(requested) ? null
                            : "Promotion reservation is owned by another principal");
        }
    }

    @Test void conflictingReplayShortCircuitsBeforeMalformedLaterFields() {
        var malformed = new ReservationReplayPolicy.Request(id, 99L, null, null, null, null, null, null);
        for (boolean bulk : new boolean[]{false, true}) {
            assertThat(ReservationReplayPolicy.failure(malformed, request(0), bulk))
                    .isEqualTo(bulk ? "Promotion reservation replay payload does not match"
                            : "Reservation replay payload does not match");
        }
        var ids = new ReservationReplayPolicy.Request(id, 1L, 2L, null, 3L,
                BigDecimal.ONE, new BigDecimal("2"), List.of(4L, 5L));
        var reversed = new ReservationReplayPolicy.Request(id, 1L, 2L, null, 3L,
                BigDecimal.ONE, new BigDecimal("2"), List.of(5L, 4L));
        assertThat(ReservationReplayPolicy.failure(ids, reversed, true))
                .isEqualTo("Promotion reservation replay payload does not match");
    }

    private ReservationReplayPolicy.Request request(int changed) {
        return new ReservationReplayPolicy.Request(changed == 1 ? UUID.randomUUID() : id,
                changed == 2 ? 99L : 1L, changed == 3 ? 99L : 2L, changed == 4 ? 99L : null,
                changed == 5 ? 99L : 3L, new BigDecimal(changed == 6 ? "9" : "1.00"),
                new BigDecimal(changed == 7 ? "9" : "2.0"), changed == 8 ? List.of(99L) : List.of(4L));
    }
    @Test void everyFingerprintFieldAndDeliberateBulkIdentityOmission() {
        for (boolean bulk : new boolean[]{false, true}) for (int field = 0; field <= 8; field++) {
            assertThat(ReservationReplayPolicy.failure(request(0), request(field), bulk))
                    .isEqualTo(field == 0 || (bulk && field == 1) ? null : bulk
                            ? "Promotion reservation replay payload does not match" : "Reservation replay payload does not match");
        }
        var stored = request(0);
        var scaleOnly = new ReservationReplayPolicy.Request(id, 1L, 2L, null, 3L,
                BigDecimal.ONE, new BigDecimal("2.000"), List.of(4L));
        assertThat(ReservationReplayPolicy.failure(stored, scaleOnly, false)).isNull();
        assertThat(ReservationReplayPolicy.failure(stored, scaleOnly, true)).isNull();
    }
}
