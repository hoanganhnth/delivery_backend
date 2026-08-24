package com.delivery.restaurant_service.service;

import com.delivery.restaurant_service.common.constants.RoleConstants;
import com.delivery.restaurant_service.dto.request.InventoryReservationRequest;
import com.delivery.restaurant_service.dto.request.UpdateMenuItemInventoryRequest;
import com.delivery.restaurant_service.dto.response.InventoryReservationResponse;
import com.delivery.restaurant_service.dto.response.MenuItemInventoryResponse;
import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.entity.MenuItemInventory;
import com.delivery.restaurant_service.entity.MenuItemInventoryReservation;
import com.delivery.restaurant_service.entity.MenuItemInventoryReservationLine;
import com.delivery.restaurant_service.exception.ResourceNotFoundException;
import com.delivery.restaurant_service.repository.MenuItemInventoryRepository;
import com.delivery.restaurant_service.repository.MenuItemInventoryReservationRepository;
import com.delivery.restaurant_service.repository.MenuItemRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Transactional menu inventory authority. Every write locks menu and inventory
 * rows in ascending menu-item order, validates the complete cart, and only then
 * changes capacity; a multi-line reservation can therefore never partially
 * consume stock.
 */
@Service
@ConditionalOnProperty(name = "app.restaurant.inventory-enabled", havingValue = "true")
public class MenuItemInventoryReservationService {

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

    @Transactional
    public InventoryReservationResponse reserve(InventoryReservationRequest request) {
        validateReservationIdentity(request);
        Map<Long, Integer> requested = normalizeLines(request.getItems());

        MenuItemInventoryReservation existing = reservationRepository.findById(request.getReservationId())
                .orElse(null);
        MenuItemInventoryReservation existingForOrder = reservationRepository.findByOrderId(request.getOrderId())
                .orElse(null);
        if (existing != null || existingForOrder != null) {
            if (existing != null && existingForOrder != null
                    && !existing.getReservationId().equals(existingForOrder.getReservationId())) {
                throw new IllegalArgumentException("Order already has a different inventory reservation");
            }
            return InventoryReservationResponse.from(
                    replay(existing != null ? existing : existingForOrder, request, requested));
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
                    || !request.getRestaurantId().equals(item.getRestaurant().getId())) {
                throw new IllegalArgumentException("Menu item belongs to another restaurant");
            }
            if (item.getStatus() != MenuItem.Status.AVAILABLE) {
                throw new IllegalArgumentException("Menu item is not available: " + itemId);
            }
            MenuItemInventory inventory = inventoryById.get(itemId);
            if (inventory == null || inventory.getOnHandQuantity() == null
                    || inventory.getReservedQuantity() == null
                    || inventory.getOnHandQuantity() < inventory.getReservedQuantity()
                    || inventory.availableQuantity() < requested.get(itemId)) {
                throw new IllegalArgumentException("Insufficient inventory for menu item " + itemId);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        MenuItemInventoryReservation reservation = MenuItemInventoryReservation.builder()
                .reservationId(request.getReservationId())
                .orderId(request.getOrderId())
                .userId(request.getUserId())
                .userPrincipalId(request.getUserPrincipalId())
                .restaurantId(request.getRestaurantId())
                .state(MenuItemInventoryReservation.State.RESERVED)
                .expiresAt(now.plus(reservationTtl))
                .createdAt(now)
                .updatedAt(now)
                .build();

        for (Map.Entry<Long, Integer> entry : requested.entrySet()) {
            MenuItemInventory inventory = inventoryById.get(entry.getKey());
            inventory.setReservedQuantity(Math.addExact(inventory.getReservedQuantity(), entry.getValue()));
            inventory.setRevision(Math.addExact(inventory.getRevision(), 1L));
            reservation.getLines().add(MenuItemInventoryReservationLine.builder()
                    .reservation(reservation)
                    .menuItemId(entry.getKey())
                    .quantity(entry.getValue())
                    .build());
        }

        reservationRepository.saveAndFlush(reservation);
        return InventoryReservationResponse.from(reservation);
    }

    @Transactional
    public InventoryReservationResponse commit(UUID reservationId, Long orderId) {
        MenuItemInventoryReservation reservation = locked(reservationId, orderId);
        if (reservation.getState() != MenuItemInventoryReservation.State.RESERVED) {
            return InventoryReservationResponse.from(reservation);
        }
        if (!LocalDateTime.now().isBefore(reservation.getExpiresAt())) {
            releaseCapacity(reservation, MenuItemInventoryReservation.State.EXPIRED);
            return InventoryReservationResponse.from(reservation);
        }

        Map<Long, MenuItemInventory> inventoryById = lockedInventory(reservation);
        for (MenuItemInventoryReservationLine line : reservation.getLines()) {
            MenuItemInventory inventory = requireInventory(inventoryById, line.getMenuItemId());
            if (inventory.getReservedQuantity() < line.getQuantity()
                    || inventory.getOnHandQuantity() < line.getQuantity()) {
                throw new IllegalStateException("Inventory ledger is inconsistent");
            }
        }
        for (MenuItemInventoryReservationLine line : reservation.getLines()) {
            MenuItemInventory inventory = inventoryById.get(line.getMenuItemId());
            inventory.setReservedQuantity(inventory.getReservedQuantity() - line.getQuantity());
            inventory.setOnHandQuantity(inventory.getOnHandQuantity() - line.getQuantity());
            inventory.setRevision(Math.addExact(inventory.getRevision(), 1L));
        }
        reservation.setState(MenuItemInventoryReservation.State.COMMITTED);
        return InventoryReservationResponse.from(reservation);
    }

    @Transactional
    public InventoryReservationResponse release(UUID reservationId, Long orderId) {
        MenuItemInventoryReservation reservation = locked(reservationId, orderId);
        if (reservation.getState() == MenuItemInventoryReservation.State.RESERVED) {
            releaseCapacity(reservation, MenuItemInventoryReservation.State.RELEASED);
        } else if (reservation.getState() == MenuItemInventoryReservation.State.COMMITTED) {
            restoreCommittedCapacity(reservation);
        }
        return InventoryReservationResponse.from(reservation);
    }

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

    @Transactional(readOnly = true)
    public MenuItemInventoryResponse getInventory(Long menuItemId) {
        requirePositive(menuItemId, "menuItemId");
        return inventoryRepository.findById(menuItemId)
                .map(com.delivery.restaurant_service.dto.response.MenuItemInventoryResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory is not configured for menu item"));
    }

    /**
     * Read-only checkout preview signal. It is deliberately advisory: reserve()
     * re-locks the same rows before writing, so a stale preview can never grant
     * capacity that is no longer present.
     */
    @Transactional(readOnly = true)
    public InventoryAvailability availability(Long restaurantId, Long menuItemId, Integer quantity) {
        if (restaurantId == null || restaurantId <= 0 || menuItemId == null || menuItemId <= 0
                || quantity == null || quantity <= 0 || quantity > MAX_LINE_QUANTITY) {
            return InventoryAvailability.unavailable(0);
        }
        MenuItem item = menuItemRepository.findById(menuItemId).orElse(null);
        MenuItemInventory inventory = inventoryRepository.findById(menuItemId).orElse(null);
        if (item == null || item.getRestaurant() == null
                || !restaurantId.equals(item.getRestaurant().getId())
                || item.getStatus() != MenuItem.Status.AVAILABLE
                || inventory == null || inventory.getOnHandQuantity() == null
                || inventory.getReservedQuantity() == null
                || inventory.getOnHandQuantity() < inventory.getReservedQuantity()) {
            return InventoryAvailability.unavailable(0);
        }
        int available = inventory.availableQuantity();
        return new InventoryAvailability(available >= quantity, available);
    }

    @Transactional
    public MenuItemInventoryResponse updateInventory(Long menuItemId,
                                                       UpdateMenuItemInventoryRequest request,
                                                       Long actorId, String role) {
        requirePositive(menuItemId, "menuItemId");
        if (request == null || request.getOnHandQuantity() == null || request.getOnHandQuantity() < 0) {
            throw new IllegalArgumentException("onHandQuantity must be zero or positive");
        }
        if (!RoleConstants.ADMIN.equals(role) && !RoleConstants.OWNER.equals(role)) {
            throw new AccessDeniedException("Only ADMIN or SHOP_OWNER may update inventory");
        }
        if (actorId == null || actorId <= 0) {
            throw new AccessDeniedException("Authenticated actor is required");
        }

        MenuItem item = menuItemRepository.findByIdForUpdate(menuItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found"));
        if (RoleConstants.OWNER.equals(role)
                && (item.getRestaurant() == null || !actorId.equals(item.getRestaurant().getCreatorId()))) {
            throw new AccessDeniedException("Actor does not own this menu item");
        }

        MenuItemInventory inventory = inventoryRepository.findByMenuItemIdForUpdate(menuItemId).orElse(null);
        if (inventory == null) {
            if (request.getExpectedRevision() != null) {
                throw new IllegalArgumentException("Inventory revision does not exist");
            }
            inventory = new MenuItemInventory();
            inventory.setMenuItemId(menuItemId);
            inventory.setOnHandQuantity(request.getOnHandQuantity());
            inventory.setReservedQuantity(0);
            inventory.setRevision(0L);
            return MenuItemInventoryResponse.from(inventoryRepository.saveAndFlush(inventory));
        }
        if (request.getExpectedRevision() == null
                || !request.getExpectedRevision().equals(inventory.getRevision())) {
            throw new IllegalArgumentException("Inventory revision is stale; reload before updating");
        }
        if (request.getOnHandQuantity() < inventory.getReservedQuantity()) {
            throw new IllegalArgumentException("onHandQuantity cannot be below reserved quantity");
        }
        inventory.setOnHandQuantity(request.getOnHandQuantity());
        inventory.setRevision(Math.addExact(inventory.getRevision(), 1L));
        return MenuItemInventoryResponse.from(inventoryRepository.saveAndFlush(inventory));
    }

    private MenuItemInventoryReservation replay(MenuItemInventoryReservation reservation,
                                                InventoryReservationRequest request,
                                                Map<Long, Integer> requested) {
        Map<Long, Integer> existing = reservation.getLines().stream().collect(Collectors.toMap(
                MenuItemInventoryReservationLine::getMenuItemId,
                MenuItemInventoryReservationLine::getQuantity,
                (left, right) -> { throw new IllegalStateException("Duplicate stored inventory line"); },
                TreeMap::new));
        if (!reservation.getReservationId().equals(request.getReservationId())
                || !reservation.getOrderId().equals(request.getOrderId())
                || !Objects.equals(reservation.getUserId(), request.getUserId())
                || !Objects.equals(reservation.getUserPrincipalId(), request.getUserPrincipalId())
                || !reservation.getRestaurantId().equals(request.getRestaurantId())
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
            if (inventory.getReservedQuantity() < line.getQuantity()) {
                throw new IllegalStateException("Inventory ledger is inconsistent");
            }
        }
        for (MenuItemInventoryReservationLine line : reservation.getLines()) {
            MenuItemInventory inventory = inventoryById.get(line.getMenuItemId());
            inventory.setReservedQuantity(inventory.getReservedQuantity() - line.getQuantity());
            inventory.setRevision(Math.addExact(inventory.getRevision(), 1L));
        }
        reservation.setState(terminal);
    }

    private void restoreCommittedCapacity(MenuItemInventoryReservation reservation) {
        Map<Long, MenuItemInventory> inventoryById = lockedInventory(reservation);
        for (MenuItemInventoryReservationLine line : reservation.getLines()) {
            MenuItemInventory inventory = requireInventory(inventoryById, line.getMenuItemId());
            inventory.setOnHandQuantity(Math.addExact(inventory.getOnHandQuantity(), line.getQuantity()));
            inventory.setRevision(Math.addExact(inventory.getRevision(), 1L));
        }
        reservation.setState(MenuItemInventoryReservation.State.RELEASED);
    }

    private MenuItemInventory requireInventory(Map<Long, MenuItemInventory> inventoryById, Long menuItemId) {
        MenuItemInventory inventory = inventoryById.get(menuItemId);
        if (inventory == null) throw new IllegalStateException("Inventory ledger is missing");
        return inventory;
    }

    private Map<Long, Integer> normalizeLines(List<InventoryReservationRequest.Line> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("At least one inventory line is required");
        }
        TreeMap<Long, Integer> normalized = new TreeMap<>();
        for (InventoryReservationRequest.Line line : lines) {
            if (line == null || line.getMenuItemId() == null || line.getMenuItemId() <= 0
                    || line.getQuantity() == null || line.getQuantity() <= 0
                    || line.getQuantity() > MAX_LINE_QUANTITY) {
                throw new IllegalArgumentException("Inventory line has an invalid item or quantity");
            }
            if (normalized.put(line.getMenuItemId(), line.getQuantity()) != null) {
                throw new IllegalArgumentException("Duplicate menu item in inventory reservation");
            }
        }
        return normalized;
    }

    private void validateReservationIdentity(InventoryReservationRequest request) {
        if (request == null || request.getReservationId() == null
                || request.getOrderId() == null || request.getOrderId() <= 0
                || request.getRestaurantId() == null || request.getRestaurantId() <= 0) {
            throw new IllegalArgumentException("Invalid inventory reservation identity");
        }
    }

    private void requirePositive(Long value, String field) {
        if (value == null || value <= 0) throw new IllegalArgumentException(field + " must be positive");
    }

    public record InventoryAvailability(boolean hasEnoughStock, Integer availableQuantity) {
        static InventoryAvailability unavailable(int availableQuantity) {
            return new InventoryAvailability(false, availableQuantity);
        }
    }
}
