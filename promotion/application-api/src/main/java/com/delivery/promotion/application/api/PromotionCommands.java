package com.delivery.promotion.application.api;

import com.delivery.promotion.domain.Voucher;
import com.delivery.promotion.domain.VoucherSelectionMode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Values crossing the application boundary. Adapter handles remain opaque generic types. */
public final class PromotionCommands {
    private PromotionCommands() {}
    public record Reserve(UUID reservationId, Long orderId, List<Long> voucherIds) {}
    public record Transition(boolean bulk, boolean commit) {}
    public record ReservationState(String state, LocalDateTime expiresAt) {}
    public record Pricing(Collection<? extends Voucher> vouchers, Long restaurantId, BigDecimal subtotal,
                          BigDecimal shipping, Collection<Long> selectedIds, VoucherSelectionMode mode,
                          LocalDateTime now) {}
    public record OrderEvent(UUID eventId, String source, String action, Long orderId,
                             UUID legacyReservationId, UUID bulkReservationId,
                             String previousStatus, String fingerprint) {}
}
