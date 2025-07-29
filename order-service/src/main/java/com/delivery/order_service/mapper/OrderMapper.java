package com.delivery.order_service.mapper;

import com.delivery.order_service.dto.request.CreateOrderRequest;
import com.delivery.order_service.dto.request.UpdateOrderRequest;
import com.delivery.order_service.dto.response.OrderItemResponse;
import com.delivery.order_service.dto.response.OrderResponse;
import com.delivery.order_service.entity.Order;
import com.delivery.order_service.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface OrderMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "subtotalPrice", ignore = true)
    @Mapping(target = "discountAmount", ignore = true)
    @Mapping(target = "shippingFee", ignore = true)
    @Mapping(target = "totalPrice", ignore = true)
    @Mapping(target = "status", constant = "PENDING")
    Order createOrderRequestToOrder(CreateOrderRequest request);

    OrderResponse orderToOrderResponse(Order order);
    
    List<OrderResponse> ordersToOrderResponses(List<Order> orders);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "restaurantId", ignore = true)
    @Mapping(target = "restaurantName", ignore = true)
    @Mapping(target = "restaurantAddress", ignore = true)
    @Mapping(target = "restaurantPhone", ignore = true)
    @Mapping(target = "subtotalPrice", ignore = true)
    @Mapping(target = "discountAmount", ignore = true)
    @Mapping(target = "totalPrice", ignore = true)
    @Mapping(target = "paymentMethod", ignore = true)
    @Mapping(target = "deliveryAddress", ignore = true)
    @Mapping(target = "deliveryLat", ignore = true)
    @Mapping(target = "deliveryLng", ignore = true)
    @Mapping(target = "customerName", ignore = true)
    @Mapping(target = "customerPhone", ignore = true)
    @Mapping(target = "items", ignore = true)
    void updateOrderFromRequest(UpdateOrderRequest request, @MappingTarget Order order);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "order", ignore = true)
    OrderItem orderItemRequestToOrderItem(CreateOrderRequest.OrderItemRequest request);
    
    OrderItemResponse orderItemToOrderItemResponse(OrderItem orderItem);
    
    List<OrderItemResponse> orderItemsToOrderItemResponses(List<OrderItem> orderItems);
}
