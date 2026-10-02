package com.delivery.restaurant.application;

import com.delivery.restaurant.application.api.*;
import com.delivery.restaurant.application.api.InventoryStorePort.Item;
import com.delivery.restaurant.application.api.InventoryStorePort.Stock;
import com.delivery.restaurant.domain.inventory.*;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Owns reservation admission, replay and locked inventory transitions. */
public final class DefaultMenuItemInventoryUseCase implements MenuItemInventoryUseCase {
    private static final int MAX_LINE_QUANTITY = 99;
    private final InventoryStorePort store;
    private final RestaurantTransactionPort transactions;
    private final Duration reservationTtl;
    private final Clock clock;

    public DefaultMenuItemInventoryUseCase(InventoryStorePort store, RestaurantTransactionPort transactions,
            Duration reservationTtl, Clock clock) {
        this.store = Objects.requireNonNull(store);
        this.transactions = Objects.requireNonNull(transactions);
        this.clock = Objects.requireNonNull(clock);
        this.reservationTtl = reservationTtl == null || reservationTtl.isNegative() || reservationTtl.isZero()
                ? Duration.ofMinutes(15) : reservationTtl;
    }

    @Override
    public InventoryReservationResult reserve(InventoryReservationCommand command) {
        return transactions.required(() -> {
            validateReservationIdentity(command);
            Map<Long, Integer> requested = normalizeLines(command.items());

            InventoryReservation existing = store.findReservation(command.reservationId())
                    .orElse(null);
            InventoryReservation existingForOrder = store.findReservationByOrder(command.orderId())
                    .orElse(null);
            if (existing != null || existingForOrder != null) {
                if (existing != null && existingForOrder != null
                        && !existing.reservationId().equals(existingForOrder.reservationId())) {
                    throw new IllegalArgumentException("Order already has a different inventory reservation");
                }
                return toResult(replay(existing != null ? existing : existingForOrder, command, requested));
            }

            List<Long> itemIds = List.copyOf(requested.keySet());
            List<Item> items = store.lockItems(itemIds);
            if (items.size() != itemIds.size()) {
                throw new IllegalArgumentException("Inventory item is missing; checkout fails closed");
            }
            Map<Long, Item> itemsById = items.stream()
                    .collect(Collectors.toMap(Item::id, Function.identity()));
            List<Stock> inventories = store.lockStocks(itemIds);
            if (inventories.size() != itemIds.size()) {
                throw new IllegalArgumentException("Inventory is not configured for every menu item");
            }
            Map<Long, Stock> inventoryById = inventories.stream()
                    .collect(Collectors.toMap(Stock::menuItemId, Function.identity()));

            for (Long itemId : itemIds) {
                Item item = itemsById.get(itemId);
                if (item == null || item.restaurantId() == null
                        || !command.restaurantId().equals(item.restaurantId())) {
                    throw new IllegalArgumentException("Menu item belongs to another restaurant");
                }
                if (!item.available()) {
                    throw new IllegalArgumentException("Menu item is not available: " + itemId);
                }
                Stock inventory = inventoryById.get(itemId);
                if (inventory == null || !capacity(inventory).canReserve(requested.get(itemId))) {
                    throw new IllegalArgumentException("Insufficient inventory for menu item " + itemId);
                }
            }

            LocalDateTime now = LocalDateTime.now(clock);
            InventoryReservation reservation = new InventoryReservation(command.reservationId(), command.orderId(),
                    command.userId(), command.userPrincipalId(), command.restaurantId(), InventoryReservation.State.RESERVED,
                    now.plus(reservationTtl), now, now, requested.entrySet().stream()
                            .map(entry -> new InventoryReservation.Line(entry.getKey(), entry.getValue())).toList());
            for (Map.Entry<Long, Integer> entry : requested.entrySet()) {
                Stock inventory = inventoryById.get(entry.getKey());
                apply(inventory, capacity(inventory).reserve(entry.getValue(), entry.getKey()));
            }
            reservation = store.insertReservation(reservation);
            return toResult(reservation);
        });
    }

    @Override
    public InventoryReservationResult commit(UUID reservationId, Long orderId) {
        return transactions.required(() -> {
            InventoryReservation reservation = locked(reservationId, orderId);
            if (reservation.state() != InventoryReservation.State.RESERVED) {
                return toResult(reservation);
            }
            if (!LocalDateTime.now(clock).isBefore(reservation.expiresAt())) {
                reservation = releaseCapacity(reservation, InventoryReservation.State.EXPIRED);
                return toResult(reservation);
            }

            Map<Long, Stock> inventoryById = lockedInventory(reservation);
            for (InventoryReservation.Line line : reservation.lines()) {
                Stock inventory = requireInventory(inventoryById, line.menuItemId());
                capacity(inventory).requireCommit(line.quantity());
            }
            for (InventoryReservation.Line line : reservation.lines()) {
                Stock inventory = inventoryById.get(line.menuItemId());
                apply(inventory, capacity(inventory).commit(line.quantity()));
            }
            store.changeReservationState(reservation.reservationId(), InventoryReservation.State.COMMITTED);
            reservation = reservation.withState(InventoryReservation.State.COMMITTED);
            return toResult(reservation);
        });
    }

    @Override
    public InventoryReservationResult release(UUID reservationId, Long orderId) {
        return transactions.required(() -> {
            InventoryReservation reservation = locked(reservationId, orderId);
            if (reservation.state() == InventoryReservation.State.RESERVED) {
                reservation = releaseCapacity(reservation, InventoryReservation.State.RELEASED);
            } else if (reservation.state() == InventoryReservation.State.COMMITTED) {
                reservation = restoreCommittedCapacity(reservation);
            }
            return toResult(reservation);
        });
    }

    @Override
    public int expireReservations() {
        return transactions.required(() -> {
            LocalDateTime now = LocalDateTime.now(clock);
            List<UUID> due = store.dueReservationIds(now);
            int expired = 0;
            for (UUID candidate : due) {
                InventoryReservation reservation = store
                        .lockReservation(candidate).orElse(null);
                if (reservation != null && reservation.state() == InventoryReservation.State.RESERVED
                        && !now.isBefore(reservation.expiresAt())) {
                    reservation = releaseCapacity(reservation, InventoryReservation.State.EXPIRED);
                    expired++;
                }
            }
            return expired;
        });
    }

    @Override
    public MenuItemInventoryResult getInventory(Long menuItemId) {
        return transactions.readOnly(() -> {
            requirePositive(menuItemId, "menuItemId");
            return store.findStock(menuItemId)
                    .map(DefaultMenuItemInventoryUseCase::toResult)
                    .orElseThrow(() -> new InventoryResourceNotFoundException(
                            "Inventory is not configured for menu item"));
        });
    }

    /**
     * Read-only checkout preview signal. It is deliberately advisory: reserve()
     * re-locks the same rows before writing, so a stale preview can never grant
     * capacity that is no longer present.
     */
    @Override
    public InventoryAvailability availability(Long restaurantId, Long menuItemId, Integer quantity) {
        return transactions.readOnly(() -> {
            if (restaurantId == null || restaurantId <= 0 || menuItemId == null || menuItemId <= 0
                    || quantity == null || quantity <= 0 || quantity > MAX_LINE_QUANTITY) {
                return new InventoryAvailability(false, 0);
            }
            Item item = store.findItem(menuItemId).orElse(null);
            Stock inventory = store.findStock(menuItemId).orElse(null);
            if (item == null || item.restaurantId() == null
                    || !restaurantId.equals(item.restaurantId())
                    || !item.available()
                    || inventory == null || !capacity(inventory).canReserve(0)) {
                return new InventoryAvailability(false, 0);
            }
            int available = inventory.capacity().availableQuantity();
            return new InventoryAvailability(available >= quantity, available);
        });
    }

    @Override
    public MenuItemInventoryResult updateInventory(Long menuItemId,
            UpdateMenuItemInventoryCommand command) {
        return transactions.required(() -> {
            requirePositive(menuItemId, "menuItemId");
            if (command == null || command.onHandQuantity() == null || command.onHandQuantity() < 0) {
                throw new IllegalArgumentException("onHandQuantity must be zero or positive");
            }
            if (command.actorRole() != RestaurantActorRole.ADMIN
                    && command.actorRole() != RestaurantActorRole.SHOP_OWNER) {
                throw new InventoryAccessDeniedException("Only ADMIN or SHOP_OWNER may update inventory");
            }
            if (command.actorId() == null || command.actorId() <= 0) {
                throw new InventoryAccessDeniedException("Authenticated actor is required");
            }

            Item item = store.lockItem(menuItemId)
                    .orElseThrow(() -> new InventoryResourceNotFoundException("Menu item not found"));
            if (command.actorRole() == RestaurantActorRole.SHOP_OWNER
                    && (item.restaurantId() == null || !command.actorId().equals(item.creatorId()))) {
                throw new InventoryAccessDeniedException("Actor does not own this menu item");
            }

            Stock inventory = store.lockStock(menuItemId).orElse(null);
            if (inventory == null) {
                if (command.expectedRevision() != null) {
                    throw new IllegalArgumentException("Inventory revision does not exist");
                }
                return toResult(store.saveStock(new Stock(menuItemId,
                        new InventoryCapacity(command.onHandQuantity(), 0, 0L))));
            }
            return toResult(store.saveStock(new Stock(menuItemId,
                    capacity(inventory).updateOnHand(command.onHandQuantity(), command.expectedRevision()))));
        });
    }

    private InventoryReservation replay(InventoryReservation reservation,
            InventoryReservationCommand command, Map<Long, Integer> requested) {
        Map<Long, Integer> existing = reservation.lines().stream().collect(Collectors.toMap(
                InventoryReservation.Line::menuItemId,
                InventoryReservation.Line::quantity,
                (left, right) -> { throw new IllegalStateException("Duplicate stored inventory line"); },
                TreeMap::new));
        if (!reservation.reservationId().equals(command.reservationId())
                || !reservation.orderId().equals(command.orderId())
                || !Objects.equals(reservation.userId(), command.userId())
                || !Objects.equals(reservation.userPrincipalId(), command.userPrincipalId())
                || !reservation.restaurantId().equals(command.restaurantId())
                || !existing.equals(requested)) {
            throw new IllegalArgumentException("Inventory reservation replay payload does not match");
        }
        return reservation;
    }

    private InventoryReservation locked(UUID reservationId, Long orderId) {
        if (reservationId == null || orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("reservationId and positive orderId are required");
        }
        InventoryReservation reservation = store.lockReservation(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory reservation not found"));
        if (!orderId.equals(reservation.orderId())) {
            throw new IllegalArgumentException("reservationId is bound to another order");
        }
        return reservation;
    }

    private Map<Long, Stock> lockedInventory(InventoryReservation reservation) {
        List<Long> ids = reservation.lines().stream()
                .map(InventoryReservation.Line::menuItemId)
                .sorted()
                .toList();
        List<Stock> inventories = store.lockStocks(ids);
        if (inventories.size() != ids.size()) {
            throw new IllegalStateException("Inventory ledger is missing a reservation line");
        }
        return inventories.stream().collect(Collectors.toMap(
                Stock::menuItemId, Function.identity(), (left, right) -> left,
                LinkedHashMap::new));
    }

    private InventoryReservation releaseCapacity(InventoryReservation reservation,
            InventoryReservation.State terminal) {
        Map<Long, Stock> inventoryById = lockedInventory(reservation);
        for (InventoryReservation.Line line : reservation.lines()) {
            Stock inventory = requireInventory(inventoryById, line.menuItemId());
            capacity(inventory).requireRelease(line.quantity());
        }
        for (InventoryReservation.Line line : reservation.lines()) {
            Stock inventory = inventoryById.get(line.menuItemId());
            apply(inventory, capacity(inventory).release(line.quantity()));
        }
        store.changeReservationState(reservation.reservationId(), terminal);
        return reservation.withState(terminal);
    }

    private InventoryReservation restoreCommittedCapacity(InventoryReservation reservation) {
        Map<Long, Stock> inventoryById = lockedInventory(reservation);
        for (InventoryReservation.Line line : reservation.lines()) {
            Stock inventory = requireInventory(inventoryById, line.menuItemId());
            apply(inventory, capacity(inventory).restoreCommitted(line.quantity()));
        }
        store.changeReservationState(reservation.reservationId(), InventoryReservation.State.RELEASED);
        return reservation.withState(InventoryReservation.State.RELEASED);
    }

    private static InventoryCapacity capacity(Stock inventory) { return inventory.capacity(); }

    private void apply(Stock inventory, InventoryCapacity capacity) {
        store.changeStock(new Stock(inventory.menuItemId(), capacity));
    }

    private Stock requireInventory(Map<Long, Stock> inventoryById, Long menuItemId) {
        Stock inventory = inventoryById.get(menuItemId);
        if (inventory == null) throw new IllegalStateException("Inventory ledger is missing");
        return inventory;
    }

    private Map<Long, Integer> normalizeLines(List<InventoryReservationLineCommand> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("At least one inventory line is required");
        }
        TreeMap<Long, Integer> normalized = new TreeMap<>();
        for (InventoryReservationLineCommand line : lines) {
            if (line == null || line.menuItemId() == null || line.menuItemId() <= 0
                    || line.quantity() == null || line.quantity() <= 0
                    || line.quantity() > MAX_LINE_QUANTITY) {
                throw new IllegalArgumentException("Inventory line has an invalid item or quantity");
            }
            if (normalized.put(line.menuItemId(), line.quantity()) != null) {
                throw new IllegalArgumentException("Duplicate menu item in inventory reservation");
            }
        }
        return normalized;
    }

    private void validateReservationIdentity(InventoryReservationCommand command) {
        if (command == null || command.reservationId() == null
                || command.orderId() == null || command.orderId() <= 0
                || command.restaurantId() == null || command.restaurantId() <= 0) {
            throw new IllegalArgumentException("Invalid inventory reservation identity");
        }
    }

    private void requirePositive(Long value, String field) {
        if (value == null || value <= 0) throw new IllegalArgumentException(field + " must be positive");
    }

    private static InventoryReservationResult toResult(InventoryReservation reservation) {
        return new InventoryReservationResult(reservation.reservationId(), reservation.orderId(),
                reservation.restaurantId(), reservation.state().name(), reservation.expiresAt(),
                reservation.lines().stream()
                        .map(line -> new InventoryReservationLineResult(line.menuItemId(), line.quantity()))
                        .toList());
    }

    private static MenuItemInventoryResult toResult(Stock inventory) {
        return new MenuItemInventoryResult(inventory.menuItemId(), inventory.capacity().onHandQuantity(),
                inventory.capacity().reservedQuantity(), inventory.capacity().availableQuantity(), inventory.capacity().revision());
    }
}
