package com.delivery.promotion.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ReservationReplayPolicy {
    private ReservationReplayPolicy() {}
    public record Request(UUID reservationId, Long orderId, Long userId, Long principalId,
                          Long restaurantId, BigDecimal subtotal, BigDecimal shipping, List<Long> voucherIds) {}

    /** Retain the two rails' original equality direction for malformed historical rows. */
    public static String orderFailure(Long storedOrderId, Long requestedOrderId, boolean bulk) {
        boolean matches = bulk ? requestedOrderId.equals(storedOrderId) : storedOrderId.equals(requestedOrderId);
        return matches ? null : "reservationId is bound to another order";
    }

    public static String principalFailure(Long storedPrincipalId, Long requestedPrincipalId) {
        return storedPrincipalId == null || !storedPrincipalId.equals(requestedPrincipalId)
                ? "Promotion reservation is owned by another principal" : null;
    }

    public static String failure(Request stored, Request requested, boolean bulk) {
        if ((!bulk && !stored.reservationId().equals(requested.reservationId()))
                || !stored.orderId().equals(requested.orderId())
                || !stored.userId().equals(requested.userId())
                || !Objects.equals(stored.principalId(), requested.principalId())
                || (!bulk && !stored.voucherIds().get(0).equals(requested.voucherIds().get(0)))
                || !stored.restaurantId().equals(requested.restaurantId())
                || stored.subtotal().compareTo(requested.subtotal()) != 0
                || stored.shipping().compareTo(requested.shipping()) != 0
                || (bulk && !stored.voucherIds().equals(requested.voucherIds()))) {
            return bulk ? "Promotion reservation replay payload does not match" : "Reservation replay payload does not match";
        }
        return null;
    }
}
