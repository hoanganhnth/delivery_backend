package com.delivery.restaurant_service.service;

import com.delivery.restaurant.application.api.*;
import com.delivery.restaurant.application.api.OrderValidationCommand;
import com.delivery.restaurant.application.api.OrderValidationResult;
import com.delivery.restaurant.application.api.OrderValidationUseCase;
import com.delivery.restaurant_service.dto.request.OrderValidationRequest;
import com.delivery.restaurant_service.dto.response.OrderValidationResultResponse;
import org.springframework.stereotype.Component;

/** HTTP DTO mapping for the canonical checkout use case. */
@Component
public final class OrderValidationHttpAdapter implements OrderCacheValidationService {
    private final OrderValidationUseCase validation;
    public OrderValidationHttpAdapter(OrderValidationUseCase validation) { this.validation = validation; }
    @Override public OrderValidationResultResponse validateOrderFromOrderService(OrderValidationRequest request) {
        OrderValidationCommand command = request == null ? null : new OrderValidationCommand(
                request.getRestaurantId(), request.getDeliveryLat(), request.getDeliveryLng(),
                request.getItems() == null ? null : request.getItems().stream()
                        .map(item -> item == null ? null : new OrderValidationLineCommand(
                                item.getMenuItemId(), item.getQuantity())).toList());
        OrderValidationResult result = validation.validate(command);
        return OrderValidationResultResponse.builder().isValid(result.isValid()).message(result.message())
                .calculatedTotal(result.calculatedTotal()).errors(result.errors().stream().map(OrderValidationHttpAdapter::map).toList())
                .restaurantInfo(map(result.restaurantInfo()))
                .itemValidations(result.itemValidations().stream().map(OrderValidationHttpAdapter::map).toList()).build();
    }
    private static OrderValidationResultResponse.ValidationError map(OrderValidationError value) {
        return OrderValidationResultResponse.ValidationError.builder()
                .field(value.field())
                .errorCode(value.errorCode())
                .message(value.message())
                .invalidValue(value.invalidValue())
                .build();
    }
    private static OrderValidationResultResponse.RestaurantInfo map(OrderValidationRestaurantInfo value) {
        if (value == null) return null;
        return OrderValidationResultResponse.RestaurantInfo.builder()
                .restaurantId(value.restaurantId())
                .creatorId(value.creatorId())
                .ownerPrincipalId(value.ownerPrincipalId())
                .restaurantName(value.restaurantName())
                .restaurantAddress(value.restaurantAddress())
                .restaurantPhone(value.restaurantPhone())
                .latitude(value.latitude())
                .longitude(value.longitude())
                .defaultPrepTimeMinutes(value.defaultPrepTimeMinutes())
                .isAvailable(value.isAvailable())
                .isOpen(value.isOpen())
                .operatingHours(value.operatingHours())
                .serviceabilityEnabled(value.serviceabilityEnabled())
                .serviceable(value.serviceable())
                .serviceabilityZoneId(value.serviceabilityZoneId())
                .serviceabilityZoneRevision(value.serviceabilityZoneRevision())
                .serviceabilityReason(value.serviceabilityReason())
                .build();
    }
    private static OrderValidationResultResponse.ItemValidationInfo map(OrderValidationItemInfo value) {
        return OrderValidationResultResponse.ItemValidationInfo.builder()
                .menuItemId(value.menuItemId())
                .menuItemName(value.menuItemName())
                .isAvailable(value.isAvailable())
                .actualPrice(value.actualPrice())
                .expectedPrice(value.expectedPrice())
                .priceMatches(value.priceMatches())
                .requestedQuantity(value.requestedQuantity())
                .availableStock(value.availableStock())
                .hasEnoughStock(value.hasEnoughStock())
                .build();
    }
}
