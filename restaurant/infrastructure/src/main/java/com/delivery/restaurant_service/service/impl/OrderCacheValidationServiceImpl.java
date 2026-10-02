package com.delivery.restaurant_service.service.impl;

import com.delivery.restaurant.domain.catalog.OperatingSchedule;
import com.delivery.restaurant.domain.catalog.RestaurantStatus;
import com.delivery.restaurant_service.dto.request.OrderValidationRequest;
import com.delivery.restaurant_service.dto.response.OrderValidationResultResponse;
import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.repository.MenuItemRepository;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import com.delivery.restaurant.application.api.MenuItemInventoryUseCase;
import com.delivery.restaurant.application.api.RestaurantServiceabilityUseCase;
import com.delivery.restaurant.application.api.ServiceabilityDecision;
import com.delivery.restaurant_service.service.OrderCacheValidationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL-canonical validation boundary used by Order checkout. */
@Service
public class OrderCacheValidationServiceImpl implements OrderCacheValidationService {

    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;
    private final RestaurantServiceabilityUseCase serviceabilityService;
    private final ObjectProvider<MenuItemInventoryUseCase> inventoryServiceProvider;
    private final Clock clock;

    public OrderCacheValidationServiceImpl(RestaurantRepository restaurantRepository,
            MenuItemRepository menuItemRepository, RestaurantServiceabilityUseCase serviceabilityService,
            ObjectProvider<MenuItemInventoryUseCase> inventoryServiceProvider, Clock clock) {
        this.restaurantRepository = restaurantRepository;
        this.menuItemRepository = menuItemRepository;
        this.serviceabilityService = serviceabilityService;
        this.inventoryServiceProvider = inventoryServiceProvider;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OrderValidationResultResponse validateOrderFromOrderService(OrderValidationRequest request) {
        List<OrderValidationResultResponse.ValidationError> errors = new ArrayList<>();
        List<OrderValidationResultResponse.ItemValidationInfo> itemValidations = new ArrayList<>();
        if (request == null || request.getRestaurantId() == null) {
            errors.add(error("restaurantId", "RESTAURANT_ID_REQUIRED", "Restaurant is required", null));
            return result(false, BigDecimal.ZERO, errors, null, itemValidations);
        }

        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId()).orElse(null);
        if (restaurant == null) {
            errors.add(error("restaurantId", "RESTAURANT_NOT_FOUND", "Restaurant does not exist",
                    request.getRestaurantId()));
            return result(false, BigDecimal.ZERO, errors,
                    unavailableRestaurant(request.getRestaurantId()), itemValidations);
        }

        boolean scheduleOpen = isOpen(restaurant, clock.instant());
        boolean acceptsOrders = restaurant.getLifecycleStatus() == RestaurantStatus.ACTIVE && scheduleOpen;
        ServiceabilityDecision serviceability = serviceabilityService.evaluate(
                restaurant.getId(), request.getDeliveryLat(), request.getDeliveryLng());
        if (serviceability.enabled() && !serviceability.serviceable()) {
            errors.add(error("deliveryCoordinate", serviceability.reason(),
                    "Delivery address is outside the service area",
                    request.getDeliveryLat() + "," + request.getDeliveryLng()));
        }
        if (!acceptsOrders) {
            String code = restaurant.getLifecycleStatus() == RestaurantStatus.ACTIVE
                    ? "RESTAURANT_CLOSED" : "RESTAURANT_NOT_ACCEPTING_ORDERS";
            errors.add(error("restaurant", code, "Restaurant is not accepting orders",
                    restaurant.getLifecycleStatus()));
        }

        Map<Long, MenuItem> canonicalItems = loadMenuItems(request);
        BigDecimal total = BigDecimal.ZERO;
        for (OrderValidationRequest.OrderItemRequest requested : safeItems(request)) {
            MenuItem item = requested == null ? null : canonicalItems.get(requested.getMenuItemId());
            OrderValidationResultResponse.ItemValidationInfo validation = validateItem(restaurant, requested, item);
            itemValidations.add(validation);
            if (!Boolean.TRUE.equals(validation.getIsAvailable())) {
                errors.add(error("menuItem", "MENU_ITEM_NOT_AVAILABLE", "Menu item is not available",
                        requested == null ? null : requested.getMenuItemId()));
            }
            if (!Boolean.TRUE.equals(validation.getHasEnoughStock())) {
                errors.add(error("stock", "INSUFFICIENT_STOCK", "Insufficient stock",
                        requested == null ? null : requested.getMenuItemId()));
            }
            if (item != null && item.getPrice() != null && requested != null
                    && requested.getQuantity() != null && requested.getQuantity() > 0) {
                total = total.add(item.getPrice().multiply(BigDecimal.valueOf(requested.getQuantity())));
            }
        }
        return result(errors.isEmpty(), total, errors,
                restaurantInfo(restaurant, scheduleOpen, acceptsOrders, serviceability), itemValidations);
    }

    private Map<Long, MenuItem> loadMenuItems(OrderValidationRequest request) {
        List<Long> ids = safeItems(request).stream()
                .filter(item -> item != null && item.getMenuItemId() != null)
                .map(OrderValidationRequest.OrderItemRequest::getMenuItemId)
                .distinct().toList();
        Map<Long, MenuItem> result = new HashMap<>();
        if (!ids.isEmpty()) menuItemRepository.findAllById(ids).forEach(item -> result.put(item.getId(), item));
        return result;
    }

    private OrderValidationResultResponse.ItemValidationInfo validateItem(
            Restaurant restaurant, OrderValidationRequest.OrderItemRequest requested, MenuItem item) {
        int quantity = requested == null || requested.getQuantity() == null ? 0 : requested.getQuantity();
        boolean belongsToRestaurant = item != null && item.getRestaurant() != null
                && restaurant.getId().equals(item.getRestaurant().getId());
        boolean canonicalDataValid = belongsToRestaurant && item.getName() != null && !item.getName().isBlank()
                && item.getPrice() != null && item.getPrice().signum() > 0;
        boolean available = canonicalDataValid && quantity > 0 && item.getStatus() == MenuItem.Status.AVAILABLE;
        Integer stock = null;
        boolean enough = quantity > 0;
        MenuItemInventoryUseCase inventory = inventoryServiceProvider.getIfAvailable();
        if (available && inventory != null) {
            var inventoryAvailability = inventory.availability(restaurant.getId(), item.getId(), quantity);
            stock = inventoryAvailability.availableQuantity();
            enough = inventoryAvailability.hasEnoughStock();
        }
        return OrderValidationResultResponse.ItemValidationInfo.builder()
                .menuItemId(requested == null ? null : requested.getMenuItemId())
                .menuItemName(item == null ? null : item.getName())
                .isAvailable(available)
                .actualPrice(item == null || item.getPrice() == null ? null : item.getPrice().doubleValue())
                .expectedPrice(null)
                .priceMatches(item != null && item.getPrice() != null && item.getPrice().signum() > 0)
                .requestedQuantity(requested == null ? null : requested.getQuantity())
                .availableStock(stock).hasEnoughStock(enough).build();
    }

    private boolean isOpen(Restaurant restaurant, Instant now) {
        try {
            return OperatingSchedule.of(restaurant.getOpeningHour(), restaurant.getClosingHour(),
                    ZoneId.of(restaurant.getTimeZone())).isOpenAt(now);
        } catch (RuntimeException invalidSchedule) {
            return false;
        }
    }

    private OrderValidationResultResponse.RestaurantInfo restaurantInfo(Restaurant restaurant,
            boolean open, boolean available, ServiceabilityDecision serviceability) {
        String hours = restaurant.getOpeningHour() == null || restaurant.getClosingHour() == null
                ? null : restaurant.getOpeningHour() + " - " + restaurant.getClosingHour();
        return OrderValidationResultResponse.RestaurantInfo.builder().restaurantId(restaurant.getId())
                .creatorId(restaurant.getCreatorId()).ownerPrincipalId(restaurant.getOwnerPrincipalId())
                .restaurantName(restaurant.getName()).restaurantAddress(restaurant.getAddress())
                .restaurantPhone(restaurant.getPhone()).latitude(restaurant.getAddressLat())
                .longitude(restaurant.getAddressLng()).defaultPrepTimeMinutes(restaurant.getDefaultPrepTimeMinutes())
                .isAvailable(available).isOpen(open).operatingHours(hours)
                .serviceabilityEnabled(serviceability.enabled())
                .serviceable(serviceability.enabled() ? serviceability.serviceable() : null)
                .serviceabilityZoneId(serviceability.zoneId()).serviceabilityZoneRevision(serviceability.zoneRevision())
                .serviceabilityReason(serviceability.reason()).build();
    }

    private OrderValidationResultResponse.RestaurantInfo unavailableRestaurant(Long id) {
        return OrderValidationResultResponse.RestaurantInfo.builder().restaurantId(id)
                .isAvailable(false).isOpen(false).build();
    }

    private OrderValidationResultResponse result(boolean valid, BigDecimal total,
            List<OrderValidationResultResponse.ValidationError> errors,
            OrderValidationResultResponse.RestaurantInfo restaurant,
            List<OrderValidationResultResponse.ItemValidationInfo> items) {
        return OrderValidationResultResponse.builder().isValid(valid)
                .message(valid ? "Order validation successful" : "Order validation failed")
                .calculatedTotal(total.doubleValue()).errors(errors).restaurantInfo(restaurant)
                .itemValidations(items).build();
    }

    private OrderValidationResultResponse.ValidationError error(String field, String code,
            String message, Object value) {
        return OrderValidationResultResponse.ValidationError.builder().field(field).errorCode(code)
                .message(message).invalidValue(value).build();
    }

    private List<OrderValidationRequest.OrderItemRequest> safeItems(OrderValidationRequest request) {
        return request == null || request.getItems() == null ? List.of() : request.getItems();
    }
}
