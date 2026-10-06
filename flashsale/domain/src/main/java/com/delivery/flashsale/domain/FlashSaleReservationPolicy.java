package com.delivery.flashsale.domain;

import java.time.LocalDateTime;
import java.util.*;

public final class FlashSaleReservationPolicy {
    private FlashSaleReservationPolicy() { }
    public enum State { RESERVED, COMMITTED, RELEASED, EXPIRED }
    public record Identity(UUID reservationId, Long orderId, Long userId, Long userPrincipalId,
                           Long restaurantId, Map<Long, Integer> lines) { }
    public static void validateQuote(FlashSaleInputs.Quote request) {
        if (request == null || request.getRestaurantId() == null || request.getItems() == null
                || request.getRestaurantId() <= 0 || !hasValidLines(request.getItems()))
            throw new IllegalArgumentException("Invalid flash-sale quote request");
    }
    public static boolean commit(State state, LocalDateTime expiresAt, java.util.function.Supplier<LocalDateTime> now) {
        if (state == State.RESERVED && !now.get().isBefore(expiresAt))
            throw new IllegalArgumentException("Flash sale reservation expired before commit");
        if (state == State.RESERVED) return true;
        if (state != State.COMMITTED)
            throw new IllegalArgumentException("Flash sale reservation cannot be committed from state " + state);
        return false;
    }
    public static boolean release(State state) { return state == State.RESERVED || state == State.COMMITTED; }
    public static boolean expire(State state, LocalDateTime expiresAt, java.util.function.Supplier<LocalDateTime> now) {
        return state == State.RESERVED && !now.get().isBefore(expiresAt);
    }
    public static void validateLock(UUID reservationId, Long orderId) {
        if (reservationId == null || orderId == null || orderId <= 0)
            throw new IllegalArgumentException("reservationId and positive orderId are required");
    }
    public static void requireOrder(Long stored, Long requested) {
        if (!stored.equals(requested)) throw new IllegalArgumentException("reservationId is bound to another order");
    }
    public static void requireLedger(Integer sold, int quantity) {
        if (sold < quantity) throw new IllegalStateException("Flash sale stock ledger is inconsistent");
    }
    public static void requireLedgerItem(boolean present) {
        if (!present) throw new IllegalStateException("Flash sale stock ledger is inconsistent");
    }
    public static void requireAllItems(int found, int requested) {
        if (found != requested) throw new IllegalArgumentException("Flash sale item not found");
    }
    public static LocalDateTime expiresAt(LocalDateTime createdAt) {
        return createdAt.plusMinutes(15);
    }
    public static Map<Long, FlashSaleInputs.Line> requestedLines(FlashSaleInputs.Quote request) {
        Map<Long, FlashSaleInputs.Line> requested = new TreeMap<>();
        for (FlashSaleInputs.Line line : request.getItems()) {
            if (requested.put(line.getFlashSaleItemId(), line) != null)
                throw new IllegalArgumentException("Duplicate flashSaleItemId");
        }
        return requested;
    }
    public static void requireExactReplay(Identity stored, FlashSaleInputs.Reservation request) {
        Map<Long, Integer> incoming = new HashMap<>();
        for (FlashSaleInputs.Line line : request.getItems()) {
            if (incoming.put(line.getFlashSaleItemId(), line.getQuantity()) != null)
                throw new IllegalArgumentException("Duplicate flashSaleItemId");
        }
        if (!stored.reservationId().equals(request.getReservationId())
                || !stored.orderId().equals(request.getOrderId())
                || !stored.userId().equals(request.getUserId())
                || !Objects.equals(stored.userPrincipalId(), request.getUserPrincipalId())
                || !stored.restaurantId().equals(request.getRestaurantId())
                || !stored.lines().equals(incoming))
            throw new IllegalArgumentException("Reservation replay payload does not match");
    }
    public static void validateReservation(FlashSaleInputs.Reservation request, boolean principalOwnershipEnforced) {
        if (request == null || request.getReservationId() == null || request.getOrderId() == null
                || request.getOrderId() <= 0 || request.getUserId() == null || request.getUserId() <= 0
                || request.getRestaurantId() == null || request.getRestaurantId() <= 0
                || !hasValidLines(request.getItems()))
            throw new IllegalArgumentException("Invalid flash sale reservation request");
        if (principalOwnershipEnforced && (request.getUserPrincipalId() == null || request.getUserPrincipalId() <= 0)) {
            throw new IllegalArgumentException("userPrincipalId is required when principal ownership is enforced");
        }
    }

    public static boolean hasValidLines(List<? extends FlashSaleInputs.Line> lines) {
        return lines != null && !lines.isEmpty()
                && lines.stream().noneMatch(line -> line == null
                        || line.getFlashSaleItemId() == null || line.getFlashSaleItemId() <= 0
                        || line.getQuantity() == null || line.getQuantity() <= 0)
                && lines.stream().map(FlashSaleInputs.Line::getFlashSaleItemId).distinct().count() == lines.size();
    }

}
