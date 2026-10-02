package com.delivery.restaurant.application.api;

import com.delivery.restaurant.domain.inventory.InventoryCapacity;
import com.delivery.restaurant.domain.inventory.InventoryReservation;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Locked persistence facts; the application owns all ledger and replay decisions. */
public interface InventoryStorePort {
    record Item(Long id, Long restaurantId, Long creatorId, boolean available) { }
    record Stock(Long menuItemId, InventoryCapacity capacity) { }
    Optional<InventoryReservation> findReservation(UUID reservationId);
    Optional<InventoryReservation> findReservationByOrder(Long orderId);
    Optional<InventoryReservation> lockReservation(UUID reservationId);
    List<UUID> dueReservationIds(LocalDateTime now);
    List<Item> lockItems(List<Long> ids);
    Optional<Item> lockItem(Long id);
    Optional<Item> findItem(Long id);
    List<Stock> lockStocks(List<Long> ids);
    Optional<Stock> lockStock(Long id);
    Optional<Stock> findStock(Long id);
    /** Stage a locked stock change in the current transaction. */
    void changeStock(Stock stock);
    Stock saveStock(Stock stock);
    InventoryReservation insertReservation(InventoryReservation reservation);
    void changeReservationState(UUID id, InventoryReservation.State state);
}
