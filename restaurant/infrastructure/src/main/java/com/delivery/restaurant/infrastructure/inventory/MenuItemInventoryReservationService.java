package com.delivery.restaurant.infrastructure.inventory;

import com.delivery.restaurant.application.api.InventoryAvailability;
import com.delivery.restaurant.application.api.InventoryReservationCommand;
import com.delivery.restaurant.application.api.InventoryReservationLineCommand;
import com.delivery.restaurant.application.api.InventoryReservationLineResult;
import com.delivery.restaurant.application.api.InventoryReservationResult;
import com.delivery.restaurant.application.api.MenuItemInventoryResult;
import com.delivery.restaurant.application.api.MenuItemInventoryUseCase;
import com.delivery.restaurant.application.api.UpdateMenuItemInventoryCommand;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import com.delivery.restaurant.domain.inventory.InventoryCapacity;
import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.entity.MenuItemInventory;
import com.delivery.restaurant_service.entity.MenuItemInventoryReservation;
import com.delivery.restaurant_service.entity.MenuItemInventoryReservationLine;
import com.delivery.restaurant_service.repository.MenuItemInventoryRepository;
import com.delivery.restaurant_service.repository.MenuItemInventoryReservationRepository;
import com.delivery.restaurant_service.repository.MenuItemRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional menu inventory authority. Every write locks menu and inventory
 * rows in ascending menu-item order, validates the complete cart, and only then
 * changes capacity; a multi-line reservation can therefore never partially
 * consume stock.
 */
@Service
@ConditionalOnProperty(name = "app.restaurant.inventory-enabled", havingValue = "true")
public class MenuItemInventoryReservationService implements MenuItemInventoryUseCase {

    private static final int MAX_LINE_QUANTITY = 99;

    private final MenuItemRepository menuItemRepository;
    private final MenuItemInventoryRepository inventoryRepository;
    private final MenuItemInventoryReservationRepository reservationRepository;
    private final Duration reservationTtl;

    public MenuItemInventoryReservationService(
            MenuItemRepository menuItemRepository,
            MenuItemInventoryRepository inventoryRepository,
            MenuItemInventoryReservationRepository reservationRepository,
            @Value("${app.restaurant.inventory-reservation-ttl:PT15M}") Duration reservationTtl) {
        this.menuItemRepository = menuItemRepository;
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
        this.reservationTtl = reservationTtl == null || reservationTtl.isNegative()
                || reservationTtl.isZero() ? Duration.ofMinutes(15) : reservationTtl;
    }

    @Override
    @Transactional
    public InventoryReservationResult reserve(InventoryReservationCommand command) {
        validateReservationIdentity(command);
        Map<Long, Integer> requested = normalizeLines(command.items());

        MenuItemInventoryReservation existing = reservationRepository.findById(command.reservationId())
                .orElse(null);
        MenuItemInventoryReservation existingForOrder = reservationRepository.findByOrderId(command.orderId())
                .orElse(null);
        if (existing != null || existingForOrder != null) {
            if (existing != null && existingForOrder != null
                    && !existing.getReservationId().equals(existingForOrder.getReservationId())) {
                throw new IllegalArgumentException("Order already has a different inventory reservation");
            }
            return toResult(replay(existing != null ? existing : existingForOrder, command, requested));
        }

        List<Long> itemIds = List.copyOf(requested.keySet());
        List<MenuItem> items = menuItemRepository.findAllByIdForUpdate(itemIds);
        if (items.size() != itemIds.size()) {
            throw new IllegalArgumentException("Inventory item is missing; checkout fails closed");
        }
        Map<Long, MenuItem> itemsById = items.stream()
                .collect(Collectors.toMap(MenuItem::getId, Function.identity()));
        List<MenuItemInventory> inventories = inventoryRepository.findAllByMenuItemIdInForUpdate(itemIds);
        if (inventories.size() != itemIds.size()) {
            throw new IllegalArgumentException("Inventory is not configured for every menu item");
        }
        Map<Long, MenuItemInventory> inventoryById = inventories.stream()
                .collect(Collectors.toMap(MenuItemInventory::getMenuItemId, Function.identity()));

        for (Long itemId : itemIds) {
            MenuItem item = itemsById.get(itemId);
            if (item == null || item.getRestaurant() == null
                    || !command.restaurantId().equals(item.getRestaurant().getId())) {
                throw new IllegalArgumentException("Menu item belongs to another restaurant");
            }
            if (item.getStatus() != MenuItem.Status.AVAILABLE) {
                throw new IllegalArgumentException("Menu item is not available: " + itemId);
            }
            MenuItemInventory inventory = inventoryById.get(itemId);
            if (inventory == null || !capacity(inventory).canReserve(requested.get(itemId))) {
                throw new IllegalArgumentException("Insufficient inventory for menu item " + itemId);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        MenuItemInventoryReservation reservation = MenuItemInventoryReservation.builder()
                .reservationId(command.reservationId())
                .orderId(command.orderId())
                .userId(command.userId())
                .userPrincipalId(command.userPrincipalId())
                .restaurantId(command.restaurantId())
                .state(MenuItemInventoryReservation.State.RESERVED)
                .expiresAt(now.plus(reservationTtl))
                .createdAt(now)
                .updatedAt(now)
                .build();

        for (Map.Entry<Long, Integer> entry : requested.entrySet()) {
            MenuItemInventory inventory = inventoryById.get(entry.getKey());
            apply(inventory, capacity(inventory).reserve(entry.getValue(), entry.getKey()));
            reservation.getLines().add(MenuItemInventoryReservationLine.builder()
                    .reservation(reservation)
                    .menuItemId(entry.getKey())
                    .quantity(entry.getValue())
                    .build());
        }

        reservationRepository.saveAndFlush(reservation);
        return toResult(reservation);
    }

    @Override
    @Transactional
    public InventoryReservationResult commit(UUID reservationId, Long orderId) {
        MenuItemInventoryReservation reservation = locked(reservationId, orderId);
        if (reservation.getState() != MenuItemInventoryReservation.State.RESERVED) {
            return toResult(reservation);
        }
        if (!LocalDateTime.now().isBefore(reservation.getExpiresAt())) {
            releaseCapacity(reservation, MenuItemInventoryReservation.State.EXPIRED);
            return toResult(reservation);
        }

        Map<Long, MenuItemInventory> inventoryById = lockedInventory(reservation);
        for (MenuItemInventoryReservationLine line : reservation.getLines()) {
            MenuItemInventory inventory = requireInventory(inventoryById, line.getMenuItemId());
            capacity(inventory).requireCommit(line.getQuantity());
        }
        for (MenuItemInventoryReservationLine line : reservation.getLines()) {
            MenuItemInventory inventory = inventoryById.get(line.getMenuItemId());
            apply(inventory, capacity(inventory).commit(line.getQuantity()));
        }
        reservation.setState(MenuItemInventoryReservation.State.COMMITTED);
        return toResult(reservation);
    }

    @Override
    @Transactional
    public InventoryReservationResult release(UUID reservationId, Long orderId) {
        MenuItemInventoryReservation reservation = locked(reservationId, orderId);
        if (reservation.getState() == MenuItemInventoryReservation.State.RESERVED) {
            releaseCapacity(reservation, MenuItemInventoryReservation.State.RELEASED);
        } else if (reservation.getState() == MenuItemInventoryReservation.State.COMMITTED) {
            restoreCommittedCapacity(reservation);
        }
        return toResult(reservation);
    }

    @Override
    @Transactional
    public int expireReservations() {
        LocalDateTime now = LocalDateTime.now();
        List<MenuItemInventoryReservation> due = reservationRepository
                .findTop100ByStateAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                        MenuItemInventoryReservation.State.RESERVED, now);
        int expired = 0;
        for (MenuItemInventoryReservation candidate : due) {
            MenuItemInventoryReservation reservation = reservationRepository
                    .findByIdForUpdate(candidate.getReservationId()).orElse(null);
            if (reservation != null && reservation.getState() == MenuItemInventoryReservation.State.RESERVED
                    && !now.isBefore(reservation.getExpiresAt())) {
                releaseCapacity(reservation, MenuItemInventoryReservation.State.EXPIRED);
                expired++;
            }
        }
        return expired;
    }

    @Override
    @Transactional(readOnly = true)
    public MenuItemInventoryResult getInventory(Long menuItemId) {
        requirePositive(menuItemId, "menuItemId");
        return inventoryRepository.findById(menuItemId)
                .map(MenuItemInventoryReservationService::toResult)
                .orElseThrow(() -> new InventoryResourceNotFoundException(
                        "Inventory is not configured for menu item"));
    }

    /**
     * Read-only checkout preview signal. It is deliberately advisory: reserve()
     * re-locks the same rows before writing, so a stale preview can never grant
     * capacity that is no longer present.
     */
    @Override
    @Transactional(readOnly = true)
    public InventoryAvailability availability(Long restaurantId, Long menuItemId, Integer quantity) {
        if (restaurantId == null || restaurantId <= 0 || menuItemId == null || menuItemId <= 0
                || quantity == null || quantity <= 0 || quantity > MAX_LINE_QUANTITY) {
            return new InventoryAvailability(false, 0);
        }
        MenuItem item = menuItemRepository.findById(menuItemId).orElse(null);
        MenuItemInventory inventory = inventoryRepository.findById(menuItemId).orElse(null);
        if (item == null || item.getRestaurant() == null
                || !restaurantId.equals(item.getRestaurant().getId())
                || item.getStatus() != MenuItem.Status.AVAILABLE
                || inventory == null || !capacity(inventory).canReserve(0)) {
            return new InventoryAvailability(false, 0);
        }
        int available = inventory.availableQuantity();
        return new InventoryAvailability(available >= quantity, available);
    }

    @Override
    @Transactional
    public MenuItemInventoryResult updateInventory(Long menuItemId,
            UpdateMenuItemInventoryCommand command) {
        requirePositive(menuItemId, "menuItemId");
        if (command == null || command.onHandQuantity() == null || command.onHandQuantity() < 0) {
            throw new IllegalArgumentException("onHandQuantity must be zero or positive");
        }
        if (command.actorRole() != RestaurantActorRole.ADMIN
                && command.actorRole() != RestaurantActorRole.SHOP_OWNER) {
            throw new AccessDeniedException("Only ADMIN or SHOP_OWNER may update inventory");
        }
        if (command.actorId() == null || command.actorId() <= 0) {
            throw new AccessDeniedException("Authenticated actor is required");
        }

        MenuItem item = menuItemRepository.findByIdForUpdate(menuItemId)
                .orElseThrow(() -> new InventoryResourceNotFoundException("Menu item not found"));
        if (command.actorRole() == RestaurantActorRole.SHOP_OWNER
                && (item.getRestaurant() == null || !command.actorId().equals(item.getRestaurant().getCreatorId()))) {
            throw new AccessDeniedException("Actor does not own this menu item");
        }

        MenuItemInventory inventory = inventoryRepository.findByMenuItemIdForUpdate(menuItemId).orElse(null);
        if (inventory == null) {
            if (command.expectedRevision() != null) {
                throw new IllegalArgumentException("Inventory revision does not exist");
            }
            inventory = new MenuItemInventory();
            inventory.setMenuItemId(menuItemId);
            inventory.setOnHandQuantity(command.onHandQuantity());
            inventory.setReservedQuantity(0);
            inventory.setRevision(0L);
            return toResult(inventoryRepository.saveAndFlush(inventory));
        }
        apply(inventory, capacity(inventory).updateOnHand(command.onHandQuantity(), command.expectedRevision()));
        return toResult(inventoryRepository.saveAndFlush(inventory));
    }

    private MenuItemInventoryReservation replay(MenuItemInventoryReservation reservation,
            InventoryReservationCommand command, Map<Long, Integer> requested) {
        Map<Long, Integer> existing = reservation.getLines().stream().collect(Collectors.toMap(
                MenuItemInventoryReservationLine::getMenuItemId,
                MenuItemInventoryReservationLine::getQuantity,
                (left, right) -> { throw new IllegalStateException("Duplicate stored inventory line"); },
                TreeMap::new));
        if (!reservation.getReservationId().equals(command.reservationId())
                || !reservation.getOrderId().equals(command.orderId())
                || !Objects.equals(reservation.getUserId(), command.userId())
                || !Objects.equals(reservation.getUserPrincipalId(), command.userPrincipalId())
                || !reservation.getRestaurantId().equals(command.restaurantId())
                || !existing.equals(requested)) {
            throw new IllegalArgumentException("Inventory reservation replay payload does not match");
        }
        return reservation;
    }

    private MenuItemInventoryReservation locked(UUID reservationId, Long orderId) {
        if (reservationId == null || orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("reservationId and positive orderId are required");
        }
        MenuItemInventoryReservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory reservation not found"));
        if (!orderId.equals(reservation.getOrderId())) {
            throw new IllegalArgumentException("reservationId is bound to another order");
        }
        return reservation;
    }

    private Map<Long, MenuItemInventory> lockedInventory(MenuItemInventoryReservation reservation) {
        List<Long> ids = reservation.getLines().stream()
                .map(MenuItemInventoryReservationLine::getMenuItemId)
                .sorted()
                .toList();
        List<MenuItemInventory> inventories = inventoryRepository.findAllByMenuItemIdInForUpdate(ids);
        if (inventories.size() != ids.size()) {
            throw new IllegalStateException("Inventory ledger is missing a reservation line");
        }
        return inventories.stream().collect(Collectors.toMap(
                MenuItemInventory::getMenuItemId, Function.identity(), (left, right) -> left,
                LinkedHashMap::new));
    }

    private void releaseCapacity(MenuItemInventoryReservation reservation,
            MenuItemInventoryReservation.State terminal) {
        Map<Long, MenuItemInventory> inventoryById = lockedInventory(reservation);
        for (MenuItemInventoryReservationLine line : reservation.getLines()) {
            MenuItemInventory inventory = requireInventory(inventoryById, line.getMenuItemId());
            capacity(inventory).requireRelease(line.getQuantity());
        }
        for (MenuItemInventoryReservationLine line : reservation.getLines()) {
            MenuItemInventory inventory = inventoryById.get(line.getMenuItemId());
            apply(inventory, capacity(inventory).release(line.getQuantity()));
        }
        reservation.setState(terminal);
    }

    private void restoreCommittedCapacity(MenuItemInventoryReservation reservation) {
        Map<Long, MenuItemInventory> inventoryById = lockedInventory(reservation);
        for (MenuItemInventoryReservationLine line : reservation.getLines()) {
            MenuItemInventory inventory = requireInventory(inventoryById, line.getMenuItemId());
            apply(inventory, capacity(inventory).restoreCommitted(line.getQuantity()));
        }
        reservation.setState(MenuItemInventoryReservation.State.RELEASED);
    }

    private static InventoryCapacity capacity(MenuItemInventory inventory) {
        return new InventoryCapacity(inventory.getOnHandQuantity(), inventory.getReservedQuantity(), inventory.getRevision());
    }

    private static void apply(MenuItemInventory inventory, InventoryCapacity capacity) {
        inventory.setOnHandQuantity(capacity.onHandQuantity());
        inventory.setReservedQuantity(capacity.reservedQuantity());
        inventory.setRevision(capacity.revision());
    }

    private MenuItemInventory requireInventory(Map<Long, MenuItemInventory> inventoryById, Long menuItemId) {
        MenuItemInventory inventory = inventoryById.get(menuItemId);
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

    private static InventoryReservationResult toResult(MenuItemInventoryReservation reservation) {
        return new InventoryReservationResult(reservation.getReservationId(), reservation.getOrderId(),
                reservation.getRestaurantId(), reservation.getState().name(), reservation.getExpiresAt(),
                reservation.getLines().stream()
                        .map(line -> new InventoryReservationLineResult(line.getMenuItemId(), line.getQuantity()))
                        .toList());
    }

    private static MenuItemInventoryResult toResult(MenuItemInventory inventory) {
        return new MenuItemInventoryResult(inventory.getMenuItemId(), inventory.getOnHandQuantity(),
                inventory.getReservedQuantity(), inventory.availableQuantity(), inventory.getRevision());
    }
}
