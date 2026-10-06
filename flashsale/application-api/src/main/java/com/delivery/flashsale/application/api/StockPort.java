package com.delivery.flashsale.application.api;

import com.delivery.flashsale.domain.*;
import com.delivery.flashsale.domain.FlashSaleReservationPolicy.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Mutable views adapt managed rows lazily; the host owns all locks and writes. */
public interface StockPort<R, Q> {
    interface Item extends FlashSaleAvailabilityPolicy.Item {
        Long menuItemId(); BigDecimal price(); void soldQuantity(int value);
    }
    record Line(Long itemId, Long menuItemId, Integer quantity, BigDecimal price) { }
    interface Reservation {
        UUID id(); Long orderId(); State state(); void state(State state);
        LocalDateTime expiresAt(); List<Line> lines(); void addLine(Line line); Identity identity();
    }
    LocalTime time(); LocalDateTime now();
    boolean principalEnforced(); void legacyFallback();
    Optional<Reservation> find(UUID id); Optional<Reservation> findOrder(Long orderId);
    Optional<Reservation> lock(UUID id);
    List<Item> items(Collection<Long> ids, boolean lock);
    Reservation create(FlashSaleInputs.Reservation request, State state, LocalDateTime createdAt, LocalDateTime expiresAt);
    void saveAndFlush(Reservation reservation); void enqueue(Reservation reservation);
    R response(Reservation reservation); Q quote(Long restaurantId, List<Line> lines);
    List<UUID> due(LocalDateTime now);
}
