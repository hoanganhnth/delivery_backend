package com.delivery.flashsale.application;

import com.delivery.flashsale.application.api.StockPort;
import com.delivery.flashsale.application.api.StockPort.*;
import com.delivery.flashsale.domain.*;
import com.delivery.flashsale.domain.FlashSaleReservationPolicy.State;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import static com.delivery.flashsale.domain.FlashSaleReservationPolicy.*;

public final class StockUseCases<R, Q> {
    private final StockPort<R, Q> port;
    public StockUseCases(StockPort<R, Q> port) { this.port = port; }
    public Q quote(FlashSaleInputs.Quote request) {
        validateQuote(request);
        var requested = requestedLines(request);
        var items = port.items(requested.keySet(), false);
        requireAllItems(items.size(), requested.size());
        LocalTime now = port.time();
        List<Line> lines = items.stream().sorted(Comparator.comparing(Item::id)).map(item -> {
            int quantity = requested.get(item.id()).getQuantity();
            FlashSaleAvailabilityPolicy.requireAvailable(item, request.getRestaurantId(), quantity, now);
            return new Line(item.id(), item.menuItemId(), quantity, item.price());
        }).toList();
        return port.quote(request.getRestaurantId(), lines);
    }
    public R reserve(FlashSaleInputs.Reservation request) {
        validateReservation(request, port.principalEnforced());
        if (!port.principalEnforced() && request.getUserPrincipalId() == null) port.legacyFallback();
        var replay = port.find(request.getReservationId());
        if (replay.isPresent()) return replay(replay.get(), request);
        var sameOrder = port.findOrder(request.getOrderId());
        if (sameOrder.isPresent()) return replay(sameOrder.get(), request);
        var requested = requestedLines(request);
        var items = port.items(requested.keySet(), true);
        requireAllItems(items.size(), requested.size());
        LocalTime now = port.time();
        LocalDateTime createdAt = port.now();
        Reservation reservation = port.create(request, State.RESERVED, createdAt, expiresAt(createdAt));
        for (Item item : items) {
            int quantity = requested.get(item.id()).getQuantity();
            FlashSaleAvailabilityPolicy.requireAvailable(item, request.getRestaurantId(), quantity, now);
            reservation.addLine(new Line(item.id(), item.menuItemId(), quantity, item.price()));
        }
        for (Item item : items) item.soldQuantity(item.soldQuantity() + requested.get(item.id()).getQuantity());
        port.saveAndFlush(reservation); port.enqueue(reservation);
        return port.response(reservation);
    }
    public R commit(UUID id, Long orderId) {
        Reservation reservation = locked(id, orderId);
        if (FlashSaleReservationPolicy.commit(reservation.state(), reservation.expiresAt(), port::now)) {
            reservation.state(State.COMMITTED); port.enqueue(reservation);
        }
        return port.response(reservation);
    }
    public R release(UUID id, Long orderId) {
        Reservation reservation = locked(id, orderId);
        if (FlashSaleReservationPolicy.release(reservation.state())) releaseCapacity(reservation, State.RELEASED);
        return port.response(reservation);
    }
    public int expire() {
        int expired = 0;
        for (UUID id : port.due(port.now())) {
            Reservation reservation = port.lock(id).orElse(null);
            if (reservation != null && FlashSaleReservationPolicy.expire(reservation.state(), reservation.expiresAt(), port::now)) {
                releaseCapacity(reservation, State.EXPIRED); expired++;
            }
        }
        return expired;
    }
    private void releaseCapacity(Reservation reservation, State terminal) {
        var ids = reservation.lines().stream().map(Line::itemId).sorted().toList();
        var items = port.items(ids, true).stream().collect(Collectors.toMap(Item::id, Function.identity()));
        for (Line line : reservation.lines()) {
            Item item = items.get(line.itemId());
            requireLedgerItem(item != null);
            requireLedger(item.soldQuantity(), line.quantity());
            item.soldQuantity(item.soldQuantity() - line.quantity());
        }
        reservation.state(terminal); port.enqueue(reservation);
    }
    private Reservation locked(UUID id, Long orderId) {
        validateLock(id, orderId);
        Reservation reservation = port.lock(id).orElseThrow(() -> new IllegalArgumentException("Flash sale reservation not found"));
        requireOrder(reservation.orderId(), orderId); return reservation;
    }
    private R replay(Reservation stored, FlashSaleInputs.Reservation request) {
        requireExactReplay(stored.identity(), request); return port.response(stored);
    }
}
