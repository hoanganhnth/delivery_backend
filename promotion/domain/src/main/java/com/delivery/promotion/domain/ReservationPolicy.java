package com.delivery.promotion.domain;

import java.time.LocalDateTime;

/** Pure reservation decisions. Legacy and bulk rails deliberately retain distinct counters. */
public final class ReservationPolicy {
    private ReservationPolicy() {}
    public record Transition(boolean apply, String failure) {}
    public record Counters(Integer global, Integer reserved, Integer used, String failure) {}
    public record WalletState(String status, boolean bindOrder, boolean stampUsedAt) {}

    public static Transition commit(String state, LocalDateTime expiresAt, LocalDateTime now, boolean bulk) {
        String label = bulk ? "Promotion" : "Voucher";
        if ("RESERVED".equals(state)) {
            if (!now.isBefore(expiresAt)) return new Transition(false, label + " reservation expired before commit");
            return new Transition(true, null);
        }
        return new Transition(false, "COMMITTED".equals(state) ? null
                : label + " reservation cannot be committed from state " + state);
    }
    public static boolean release(String state) {
        return "RESERVED".equals(state) || "COMMITTED".equals(state);
    }
    public static boolean expire(String state, LocalDateTime expiresAt, LocalDateTime now) {
        return "RESERVED".equals(state) && !now.isBefore(expiresAt);
    }
    public static int safeCount(Integer value) { return value == null ? 0 : Math.max(0, value); }
    public static Counters reserve(Integer global, Integer reserved, Integer used, boolean bulk) {
        return bulk ? new Counters(safeCount(global) + 1, safeCount(reserved) + 1, used, null)
                : new Counters(global + 1, reserved, used, null);
    }
    public static Counters transition(Integer global, Integer reserved, Integer used,
                                      boolean committed, boolean commit, boolean bulk) {
        if (commit) return new Counters(global, Math.max(0, safeCount(reserved) - 1), safeCount(used) + 1, null);
        int quantity = bulk ? safeCount(global) : global;
        if (quantity <= 0) return new Counters(global, reserved, used, "Voucher usage counter is inconsistent");
        if (!bulk) return new Counters(quantity - 1, reserved, used, null);
        return new Counters(quantity - 1, committed ? reserved : Integer.valueOf(Math.max(0, safeCount(reserved) - 1)),
                committed ? Integer.valueOf(Math.max(0, safeCount(used) - 1)) : used, null);
    }
    public static WalletState wallet(Integer used, Integer reserved, Integer limit) {
        int u = safeCount(used), r = safeCount(reserved), l = limit == null ? 1 : limit;
        return new WalletState(r > 0 ? "RESERVED" : u >= l ? "USED" : "SAVED", r > 0, u > 0);
    }
}
