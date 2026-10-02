package com.delivery.restaurant.infrastructure.inventory;

import com.delivery.restaurant.application.api.InventoryStorePort;
import com.delivery.restaurant.domain.inventory.InventoryCapacity;
import com.delivery.restaurant.domain.inventory.InventoryReservation;
import com.delivery.restaurant_service.entity.*;
import com.delivery.restaurant_service.repository.MenuItemRepository;
import com.delivery.restaurant_service.repository.MenuItemInventoryRepository;
import com.delivery.restaurant_service.repository.MenuItemInventoryReservationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** JPA locks, entity mapping and persistence within the core transaction. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.restaurant.inventory-enabled", havingValue = "true")
public class JpaInventoryAdapter implements InventoryStorePort {
    private final MenuItemRepository items;
    private final MenuItemInventoryRepository stocks;
    private final MenuItemInventoryReservationRepository reservations;

    @Override public Optional<InventoryReservation> findReservation(UUID id) { return reservations.findById(id).map(JpaInventoryAdapter::reservation); }
    @Override public Optional<InventoryReservation> findReservationByOrder(Long id) { return reservations.findByOrderId(id).map(JpaInventoryAdapter::reservation); }
    @Override public Optional<InventoryReservation> lockReservation(UUID id) { return reservations.findByIdForUpdate(id).map(JpaInventoryAdapter::reservation); }
    @Override public List<UUID> dueReservationIds(LocalDateTime now) {
        return reservations.findDueReservationIds(MenuItemInventoryReservation.State.RESERVED, now,
                org.springframework.data.domain.PageRequest.of(0, 100));
    }
    @Override public List<Item> lockItems(List<Long> ids) { return items.findAllByIdForUpdate(ids).stream().map(JpaInventoryAdapter::item).toList(); }
    @Override public Optional<Item> lockItem(Long id) { return items.findByIdForUpdate(id).map(JpaInventoryAdapter::item); }
    @Override public Optional<Item> findItem(Long id) { return items.findById(id).map(JpaInventoryAdapter::item); }
    @Override public List<Stock> lockStocks(List<Long> ids) { return stocks.findAllByMenuItemIdInForUpdate(ids).stream().map(JpaInventoryAdapter::stock).toList(); }
    @Override public Optional<Stock> lockStock(Long id) { return stocks.findByMenuItemIdForUpdate(id).map(JpaInventoryAdapter::stock); }
    @Override public Optional<Stock> findStock(Long id) { return stocks.findById(id).map(JpaInventoryAdapter::stock); }
    @Override public void changeStock(Stock value) {
        var row = stocks.findById(value.menuItemId()).orElseThrow(() -> new IllegalStateException("Inventory ledger is missing"));
        apply(row, value);
    }
    @Override public Stock saveStock(Stock value) {
        var row = stocks.findById(value.menuItemId()).orElseGet(MenuItemInventory::new);
        apply(row, value);
        return stock(stocks.saveAndFlush(row));
    }
    @Override public InventoryReservation insertReservation(InventoryReservation value) {
        var row = MenuItemInventoryReservation.builder().reservationId(value.reservationId()).orderId(value.orderId())
                .userId(value.userId()).userPrincipalId(value.userPrincipalId()).restaurantId(value.restaurantId())
                .state(MenuItemInventoryReservation.State.valueOf(value.state().name())).expiresAt(value.expiresAt())
                .createdAt(value.createdAt()).updatedAt(value.updatedAt()).build();
        for (var line : value.lines()) {
            row.getLines().add(MenuItemInventoryReservationLine.builder().reservation(row)
                    .menuItemId(line.menuItemId()).quantity(line.quantity()).build());
        }
        return reservation(reservations.saveAndFlush(row));
    }
    @Override public void changeReservationState(UUID id, InventoryReservation.State state) {
        var row = reservations.findById(id).orElseThrow(() -> new IllegalArgumentException("Inventory reservation not found"));
        row.setState(MenuItemInventoryReservation.State.valueOf(state.name()));
    }

    private static void apply(MenuItemInventory row, Stock value) {
        row.setMenuItemId(value.menuItemId()); row.setOnHandQuantity(value.capacity().onHandQuantity());
        row.setReservedQuantity(value.capacity().reservedQuantity()); row.setRevision(value.capacity().revision());
    }
    private static Item item(MenuItem row) {
        var restaurant = row.getRestaurant();
        return new Item(row.getId(), restaurant == null ? null : restaurant.getId(),
                restaurant == null ? null : restaurant.getCreatorId(), row.getStatus() == MenuItem.Status.AVAILABLE);
    }
    private static Stock stock(MenuItemInventory row) {
        return new Stock(row.getMenuItemId(), new InventoryCapacity(row.getOnHandQuantity(), row.getReservedQuantity(), row.getRevision()));
    }
    private static InventoryReservation reservation(MenuItemInventoryReservation row) {
        return new InventoryReservation(row.getReservationId(), row.getOrderId(), row.getUserId(), row.getUserPrincipalId(),
                row.getRestaurantId(), InventoryReservation.State.valueOf(row.getState().name()), row.getExpiresAt(),
                row.getCreatedAt(), row.getUpdatedAt(), row.getLines().stream()
                        .map(line -> new InventoryReservation.Line(line.getMenuItemId(), line.getQuantity())).toList());
    }
}
