package com.delivery.order_service.service;

import com.delivery.order_service.dto.event.OrderCreatedEvent;
import com.delivery.order_service.dto.event.OrderCancelledEvent;
import com.delivery.order_service.entity.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * ✅ Event Publisher Service cho Order Service theo Backend Instructions
 */
@Slf4j
@Service
public class OrderEventPublisher {

    private static final String ORDER_CREATED_EVENT = "ORDER_CREATED";
    private static final String ORDER_CANCELLED_EVENT = "ORDER_CANCELLED";

    private final OrderOutboxService outboxService;

    @Value("${app.kafka.topics.order-created:order.created}")
    private String orderCreatedTopic;

    @Value("${app.kafka.topics.order-cancelled:order.cancelled}")
    private String orderCancelledTopic;

    @Value("${app.kafka.topics.refund-eligible:order.refund-eligible}")
    private String refundEligibilityTopic;

    public OrderEventPublisher(OrderOutboxService outboxService) {
        this.outboxService = outboxService;
    }
    
    /**
     * Publish OrderCreatedEvent khi order được tạo thành công
     */
    public void publishOrderCreatedEvent(Order order) {
        requirePersistedOrder(order);
        OrderCreatedEvent event = mapOrderToEvent(order);
        outboxService.enqueue(
                ORDER_CREATED_EVENT,
                order.getId().toString(),
                orderCreatedTopic,
                order.getId().toString(),
                event);
        log.info("Queued OrderCreatedEvent in transactional outbox for order {}", order.getId());
    }
    
    /**
     * Publish OrderCancelledEvent khi order bị hủy
     */
    public void publishOrderCancelledEvent(Order order, String previousStatus, Long cancelledBy) {
        publishOrderCancelledEvent(order, previousStatus, cancelledBy, "LEGACY_ACTOR", "ORDER_CANCELLED");
    }

    /**
     * Publish an order cancellation with the source and stable reason code
     * required by the refund eligibility policy.  The free-form reason remains
     * an audit/display field; consumers must branch on the typed code/source.
     */
    public void publishOrderCancelledEvent(Order order, String previousStatus, Long cancelledBy,
                                           String cancelledBySource, String cancelReasonCode) {
        requirePersistedOrder(order);
        OrderCancelledEvent event = mapOrderToCancelledEvent(order, previousStatus, cancelledBy,
                "CANCELLED", cancelledBySource, cancelReasonCode);
        outboxService.enqueue(
                ORDER_CANCELLED_EVENT,
                order.getId().toString(),
                orderCancelledTopic,
                order.getId().toString(),
                event);
        log.info("Queued OrderCancelledEvent in transactional outbox for order {}", order.getId());
    }

    /**
     * A no-shipper terminal outcome is intentionally not represented as an
     * Order cancellation: Order and Delivery remain SHIPPER_NOT_FOUND.  It
     * still needs the same immutable money/reservation snapshot so settlement
     * and checkout compensation can converge without changing fulfilment
     * semantics.
     */
    public void publishRefundEligibilityEvent(Order order, String previousStatus, String reason) {
        requirePersistedOrder(order);
        OrderCancelledEvent event = mapOrderToCancelledEvent(order, previousStatus, null,
                "SHIPPER_NOT_FOUND", "SYSTEM", "SHIPPER_NOT_FOUND");
        event.setCancelReason(reason == null || reason.isBlank()
                ? "No shipper available" : reason);
        outboxService.enqueue(
                "REFUND_ELIGIBLE",
                order.getId().toString(),
                refundEligibilityTopic,
                order.getId().toString(),
                event);
        log.info("Queued refund eligibility event for no-shipper order {}", order.getId());
    }

    private void requirePersistedOrder(Order order) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("A persisted order is required before enqueueing an event");
        }
    }
    
    /**
     * Map Order entity to OrderCancelledEvent
     */
    private OrderCancelledEvent mapOrderToCancelledEvent(Order order, String previousStatus,
                                                         Long cancelledBy, String currentStatus,
                                                         String cancelledBySource, String cancelReasonCode) {
        // Build cancel event manually to match structure
        OrderCancelledEvent cancelEvent = new OrderCancelledEvent();
        cancelEvent.setOrderId(order.getId());
        cancelEvent.setUserId(order.getUserId());
        cancelEvent.setUserPrincipalId(order.getUserPrincipalId());
        cancelEvent.setRestaurantId(order.getRestaurantId());
        cancelEvent.setPreviousStatus(previousStatus);
        cancelEvent.setCurrentStatus(currentStatus);
        cancelEvent.setCancelReason(order.getCancelReason());
        cancelEvent.setCancelledBy(cancelledBy);
        cancelEvent.setCancelledBySource(cancelledBySource);
        cancelEvent.setCancelReasonCode(cancelReasonCode);
        cancelEvent.setCancelledAt(order.getUpdatedAt() != null ? order.getUpdatedAt() : LocalDateTime.now());
        cancelEvent.setShipperId(order.getShipperId());
        cancelEvent.setHasActiveDelivery(order.getShipperId() != null);
        cancelEvent.setVoucherReservationId(order.getVoucherReservationId());
        cancelEvent.setPromotionReservationId(order.getPromotionReservationId());
        cancelEvent.setFlashSaleReservationId(order.getFlashSaleReservationId());
        cancelEvent.setSubtotalPrice(order.getSubtotalPrice());
        cancelEvent.setDiscountAmount(order.getDiscountAmount());
        cancelEvent.setShippingFee(order.getShippingFee());
        cancelEvent.setTotalPrice(order.getTotalPrice());
        cancelEvent.setItemDiscount(order.getItemDiscount());
        cancelEvent.setShippingDiscount(order.getShippingDiscount());
        cancelEvent.setCustomerShippingFee(order.getCustomerShippingFee());
        cancelEvent.setGrossShippingFee(order.getGrossShippingFee());
        cancelEvent.setPlatformSubsidy(order.getPlatformSubsidy());
        cancelEvent.setShopDiscount(order.getShopDiscount());
        cancelEvent.setAppliedVouchers(parseBreakdown(order.getPromotionBreakdown()));
        cancelEvent.setPaymentMethod(order.getPaymentMethod());
        cancelEvent.setCreatedAt(order.getCreatedAt());
        cancelEvent.setUpdatedAt(order.getUpdatedAt());

        if (order.getItems() != null) {
            java.util.List<java.util.Map<String, Object>> items = order.getItems().stream().map(item -> {
                java.util.Map<String, Object> map = new java.util.HashMap<>();
                map.put("flashSaleItemId", item.getFlashSaleItemId());
                map.put("quantity", item.getQuantity());
                return map;
            }).toList();
            cancelEvent.setItems(items);
        }
        
        return cancelEvent;
    }
    
    /**
     * Map Order entity to OrderCreatedEvent
     */
    private OrderCreatedEvent mapOrderToEvent(Order order) {
        OrderCreatedEvent event = new OrderCreatedEvent();
        
        // Order basic info
        event.setOrderId(order.getId());
        event.setUserId(order.getUserId());
        event.setUserPrincipalId(order.getUserPrincipalId());
        event.setRestaurantId(order.getRestaurantId());
        event.setStatus(order.getStatus().name());
        
        // Financial info
        event.setSubtotalPrice(order.getSubtotalPrice());
        event.setDiscountAmount(order.getDiscountAmount());
        event.setShippingFee(order.getShippingFee());
        event.setTotalPrice(order.getTotalPrice());
        event.setPaymentMethod(order.getPaymentMethod());
        
        // Delivery location info
        event.setDeliveryAddress(order.getDeliveryAddress());
        event.setDeliveryLat(order.getDeliveryLat());
        event.setDeliveryLng(order.getDeliveryLng());
        
        // Pickup location info
        event.setPickupLat(order.getPickupLat());
        event.setPickupLng(order.getPickupLng());
        
        // Restaurant info
        event.setRestaurantName(order.getRestaurantName());
        event.setRestaurantAddress(order.getRestaurantAddress());
        event.setRestaurantPhone(order.getRestaurantPhone());
        
        // Customer info
        event.setCustomerName(order.getCustomerName());
        event.setCustomerPhone(order.getCustomerPhone());
        event.setNotes(order.getNotes());
        
        // Timestamps
        event.setCreatedAt(order.getCreatedAt());
        event.setCreatorId(order.getCreatorId());
        event.setCreatorPrincipalId(order.getCreatorPrincipalId());
        event.setVoucherReservationId(order.getVoucherReservationId());
        event.setPromotionReservationId(order.getPromotionReservationId());
        event.setFlashSaleReservationId(order.getFlashSaleReservationId());
        event.setItemDiscount(order.getItemDiscount());
        event.setShippingDiscount(order.getShippingDiscount());
        event.setCustomerShippingFee(order.getCustomerShippingFee());
        event.setGrossShippingFee(order.getGrossShippingFee());
        event.setPlatformSubsidy(order.getPlatformSubsidy());
        event.setShopDiscount(order.getShopDiscount());
        event.setAppliedVouchers(parseBreakdown(order.getPromotionBreakdown()));
        
        return event;
    }

    private java.util.List<java.util.Map<String, Object>> parseBreakdown(String json) {
        if (json == null || json.isBlank()) return java.util.List.of();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>() {});
        } catch (Exception ignored) {
            return java.util.List.of();
        }
    }
}
