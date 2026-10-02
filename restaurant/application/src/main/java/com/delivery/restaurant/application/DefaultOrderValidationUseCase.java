package com.delivery.restaurant.application;

import com.delivery.restaurant.application.api.*;
import com.delivery.restaurant.domain.catalog.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

/** Canonical checkout decisions from a consistent database snapshot. */
public final class DefaultOrderValidationUseCase implements OrderValidationUseCase {
    private final OrderValidationCatalogPort catalog;
    private final RestaurantServiceabilityUseCase serviceabilityService;
    private final Supplier<MenuItemInventoryUseCase> inventoryProvider;
    private final Clock clock;
    private final RestaurantTransactionPort transactions;
    public DefaultOrderValidationUseCase(OrderValidationCatalogPort catalog, RestaurantServiceabilityUseCase serviceability,
            Supplier<MenuItemInventoryUseCase> inventoryProvider, Clock clock, RestaurantTransactionPort transactions) {
        this.catalog = Objects.requireNonNull(catalog); this.serviceabilityService = Objects.requireNonNull(serviceability);
        this.inventoryProvider = Objects.requireNonNull(inventoryProvider); this.clock = Objects.requireNonNull(clock);
        this.transactions = Objects.requireNonNull(transactions);
    }

    @Override
    public OrderValidationResult validate(OrderValidationCommand request) {
        return transactions.repeatableRead(() -> {
            List<OrderValidationError> errors = new ArrayList<>();
            List<OrderValidationItemInfo> itemValidations = new ArrayList<>();
            if (request == null || request.restaurantId() == null) {
                errors.add(error("restaurantId", "RESTAURANT_ID_REQUIRED", "Restaurant is required", null));
                return result(false, BigDecimal.ZERO, errors, null, itemValidations);
            }

            RestaurantSnapshot restaurant = catalog.findRestaurant(request.restaurantId()).orElse(null);
            if (restaurant == null) {
                errors.add(error("restaurantId", "RESTAURANT_NOT_FOUND", "Restaurant does not exist",
                        request.restaurantId()));
                return result(false, BigDecimal.ZERO, errors,
                        unavailableRestaurant(request.restaurantId()), itemValidations);
            }

            boolean scheduleOpen = isOpen(restaurant, clock.instant());
            boolean acceptsOrders = restaurant.lifecycleStatus() == RestaurantStatus.ACTIVE && scheduleOpen;
            ServiceabilityDecision serviceability = serviceabilityService.evaluate(
                    restaurant.id(), request.deliveryLat(), request.deliveryLng());
            if (serviceability.enabled() && !serviceability.serviceable()) {
                errors.add(error("deliveryCoordinate", serviceability.reason(),
                        "Delivery address is outside the service area",
                        request.deliveryLat() + "," + request.deliveryLng()));
            }
            if (!acceptsOrders) {
                String code = restaurant.lifecycleStatus() == RestaurantStatus.ACTIVE
                        ? "RESTAURANT_CLOSED" : "RESTAURANT_NOT_ACCEPTING_ORDERS";
                errors.add(error("restaurant", code, "Restaurant is not accepting orders",
                        restaurant.lifecycleStatus()));
            }

            Map<Long, MenuItemSnapshot> canonicalItems = loadMenuItems(request);
            BigDecimal total = BigDecimal.ZERO;
            for (OrderValidationLineCommand requested : safeItems(request)) {
                MenuItemSnapshot item = requested == null ? null : canonicalItems.get(requested.menuItemId());
                OrderValidationItemInfo validation = validateItem(restaurant, requested, item);
                itemValidations.add(validation);
                if (!Boolean.TRUE.equals(validation.isAvailable())) {
                    errors.add(error("menuItem", "MENU_ITEM_NOT_AVAILABLE", "Menu item is not available",
                            requested == null ? null : requested.menuItemId()));
                }
                if (!Boolean.TRUE.equals(validation.hasEnoughStock())) {
                    errors.add(error("stock", "INSUFFICIENT_STOCK", "Insufficient stock",
                            requested == null ? null : requested.menuItemId()));
                }
                if (item != null && item.price() != null && requested != null
                        && requested.quantity() != null && requested.quantity() > 0) {
                    total = total.add(item.price().multiply(BigDecimal.valueOf(requested.quantity())));
                }
            }
            return result(errors.isEmpty(), total, errors,
                    restaurantInfo(restaurant, scheduleOpen, acceptsOrders, serviceability), itemValidations);

        });
    }

    private Map<Long, MenuItemSnapshot> loadMenuItems(OrderValidationCommand request) {
        List<Long> ids = safeItems(request).stream()
                .filter(item -> item != null && item.menuItemId() != null)
                .map(OrderValidationLineCommand::menuItemId)
                .distinct().toList();
        Map<Long, MenuItemSnapshot> result = new HashMap<>();
        if (!ids.isEmpty()) catalog.findItems(ids).forEach(item -> result.put(item.id(), item));
        return result;
    }

    private OrderValidationItemInfo validateItem(
            RestaurantSnapshot restaurant, OrderValidationLineCommand requested, MenuItemSnapshot item) {
        int quantity = requested == null || requested.quantity() == null ? 0 : requested.quantity();
        boolean belongsToRestaurant = item != null && item.restaurantId() != null
                && restaurant.id().equals(item.restaurantId());
        boolean canonicalDataValid = belongsToRestaurant && item.name() != null && !item.name().isBlank()
                && item.price() != null && item.price().signum() > 0;
        boolean available = canonicalDataValid && quantity > 0 && item.status() == MenuItemStatus.AVAILABLE;
        Integer stock = null;
        boolean enough = quantity > 0;
        MenuItemInventoryUseCase inventory = inventoryProvider.get();
        if (available && inventory != null) {
            var inventoryAvailability = inventory.availability(restaurant.id(), item.id(), quantity);
            stock = inventoryAvailability.availableQuantity();
            enough = inventoryAvailability.hasEnoughStock();
        }
        return new OrderValidationItemInfo(
                requested == null ? null : requested.menuItemId(), item == null ? null : item.name(), available,
                item == null || item.price() == null ? null : item.price().doubleValue(), null,
                item != null && item.price() != null && item.price().signum() > 0,
                requested == null ? null : requested.quantity(), stock, enough);
    }

    private boolean isOpen(RestaurantSnapshot restaurant, Instant now) {
        try {
            return OperatingSchedule.of(restaurant.openingHour(), restaurant.closingHour(),
                    ZoneId.of(restaurant.timeZone())).isOpenAt(now);
        } catch (RuntimeException invalidSchedule) {
            return false;
        }
    }

    private OrderValidationRestaurantInfo restaurantInfo(RestaurantSnapshot restaurant,
            boolean open, boolean available, ServiceabilityDecision serviceability) {
        String hours = restaurant.openingHour() == null || restaurant.closingHour() == null
                ? null : restaurant.openingHour() + " - " + restaurant.closingHour();
        return new OrderValidationRestaurantInfo(restaurant.id(), restaurant.creatorId(), restaurant.ownerPrincipalId(),
                restaurant.name(), restaurant.address(), restaurant.phone(), restaurant.latitude(), restaurant.longitude(),
                restaurant.defaultPrepTimeMinutes(), available, open, hours, serviceability.enabled(),
                serviceability.enabled() ? serviceability.serviceable() : null, serviceability.zoneId(),
                serviceability.zoneRevision(), serviceability.reason());
    }

    private OrderValidationRestaurantInfo unavailableRestaurant(Long id) {
        return new OrderValidationRestaurantInfo(id, null, null, null, null, null, null, null, null,
                false, false, null, null, null, null, null, null);
    }

    private OrderValidationResult result(boolean valid, BigDecimal total,
            List<OrderValidationError> errors,
            OrderValidationRestaurantInfo restaurant,
            List<OrderValidationItemInfo> items) {
        return new OrderValidationResult(valid, valid ? "Order validation successful" : "Order validation failed",
                total.doubleValue(), errors, restaurant, items);
    }

    private OrderValidationError error(String field, String code, String message, Object value) {
        return new OrderValidationError(field, code, message, value);
    }

    private List<OrderValidationLineCommand> safeItems(OrderValidationCommand request) {
        return request == null || request.items() == null ? List.of() : request.items();
    }
}
