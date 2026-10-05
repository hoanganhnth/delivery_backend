package com.delivery.promotion.domain;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;

class ReservationPolicyTest {
    @Test void terminalStatesDoNotInspectMalformedExpiryAndReservedStatesStillFail() {
        for (boolean bulk : new boolean[]{false, true}) {
            assertThat(ReservationPolicy.commit("COMMITTED", null, null, bulk))
                    .isEqualTo(new ReservationPolicy.Transition(false, null));
            assertThat(ReservationPolicy.commit("RELEASED", null, null, bulk).failure())
                    .isEqualTo((bulk ? "Promotion" : "Voucher") + " reservation cannot be committed from state RELEASED");
            assertThat(ReservationPolicy.expire("COMMITTED", null, null)).isFalse();
            assertThatNullPointerException().isThrownBy(() -> ReservationPolicy.commit("RESERVED", null, LocalDateTime.now(), bulk));
            assertThatNullPointerException().isThrownBy(() -> ReservationPolicy.expire("RESERVED", null, LocalDateTime.now()));
        }
    }

    @Test void transitionMatrixMatchesOriginalGuardsAndMessages() {
        LocalDateTime expiry = LocalDateTime.of(2026, 10, 5, 1, 0);
        for (String state : new String[]{null, "RESERVED", "COMMITTED", "RELEASED", "EXPIRED"}) {
            for (LocalDateTime now : new LocalDateTime[]{expiry.minusNanos(1), expiry, expiry.plusNanos(1)}) {
                for (boolean bulk : new boolean[]{false, true}) {
                    String label = bulk ? "Promotion" : "Voucher";
                    String failure = "RESERVED".equals(state) ? (!now.isBefore(expiry) ? label + " reservation expired before commit" : null)
                            : "COMMITTED".equals(state) ? null : label + " reservation cannot be committed from state " + state;
                    assertThat(ReservationPolicy.commit(state, expiry, now, bulk))
                            .isEqualTo(new ReservationPolicy.Transition("RESERVED".equals(state) && now.isBefore(expiry), failure));
                    assertThat(ReservationPolicy.release(state)).isEqualTo("RESERVED".equals(state) || "COMMITTED".equals(state));
                    assertThat(ReservationPolicy.expire(state, expiry, now)).isEqualTo("RESERVED".equals(state) && !now.isBefore(expiry));
                }
            }
        }
    }
    @Test void exhaustiveCounterMatrixRetainsNormalizationClampsOverflowAndUntouchedValues() {
        Integer[] values = {null, -1, 0, 1, 2, Integer.MAX_VALUE};
        for (Integer global : values) for (Integer reserved : values) for (Integer used : values) {
            int g = safe(global), r = safe(reserved), u = safe(used);
            assertThat(ReservationPolicy.reserve(global, reserved, used, true))
                    .isEqualTo(new ReservationPolicy.Counters(g + 1, r + 1, used, null));
            if (global != null) assertThat(ReservationPolicy.reserve(global, reserved, used, false))
                    .isEqualTo(new ReservationPolicy.Counters(global + 1, reserved, used, null));
            else assertThatNullPointerException().isThrownBy(() -> ReservationPolicy.reserve(null, reserved, used, false));
            for (boolean committed : new boolean[]{false, true}) {
                assertThat(ReservationPolicy.transition(global, reserved, used, committed, true, true))
                        .isEqualTo(new ReservationPolicy.Counters(global, Math.max(0, r - 1), u + 1, null));
                assertThat(ReservationPolicy.transition(global, reserved, used, committed, false, true))
                        .isEqualTo(g <= 0 ? new ReservationPolicy.Counters(global, reserved, used, "Voucher usage counter is inconsistent")
                                : new ReservationPolicy.Counters(g - 1, committed ? reserved : Integer.valueOf(Math.max(0, r - 1)), committed ? Integer.valueOf(Math.max(0, u - 1)) : used, null));
                if (global != null) assertThat(ReservationPolicy.transition(global, reserved, used, committed, false, false))
                        .isEqualTo(global <= 0 ? new ReservationPolicy.Counters(global, reserved, used, "Voucher usage counter is inconsistent")
                                : new ReservationPolicy.Counters(global - 1, reserved, used, null));
                else assertThatNullPointerException().isThrownBy(() -> ReservationPolicy.transition(null, reserved, used, committed, false, false));
            }
            for (Integer limit : values) assertThat(ReservationPolicy.wallet(used, reserved, limit))
                    .isEqualTo(new ReservationPolicy.WalletState(r > 0 ? "RESERVED" : u >= (limit == null ? 1 : limit) ? "USED" : "SAVED", r > 0, u > 0));
        }
    }
    private int safe(Integer value) { return value == null ? 0 : Math.max(0, value); }
}
