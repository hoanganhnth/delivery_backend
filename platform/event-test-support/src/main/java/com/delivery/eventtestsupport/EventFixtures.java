package com.delivery.eventtestsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.delivery.eventtestsupport.TestEvents.DeliveryCompletedEvent;
import static com.delivery.eventtestsupport.TestEvents.DeliveryExceptionReportedEvent;
import static com.delivery.eventtestsupport.TestEvents.DeliveryStatusUpdatedEvent;
import static com.delivery.eventtestsupport.TestEvents.IdentityStatusChangedEvent;
import static com.delivery.eventtestsupport.TestEvents.OrderCancelledEvent;
import static com.delivery.eventtestsupport.TestEvents.OrderCreatedEvent;
import static com.delivery.eventtestsupport.TestEvents.PaymentEvent;
import static com.delivery.eventtestsupport.TestEvents.RestaurantOrderDecisionEvent;
import static com.delivery.eventtestsupport.TestEvents.ShipperFoundEvent;
import static com.delivery.eventtestsupport.TestEvents.ShipperLocationUpdatedEvent;
import static com.delivery.eventtestsupport.TestEvents.ShipperMatchResult;
import static com.delivery.eventtestsupport.TestEvents.ShipperNotFoundEvent;

/**
 * Deterministic, realistic fixtures for the common Delivery event shapes.
 *
 * <p>Values intentionally resemble a Ho Chi Minh City order and use VND
 * amounts. The records are independent from service packages so a consumer
 * test can deserialize them into the service-owned DTO under test.</p>
 */
public final class EventFixtures {

    public static final long ORDER_ID = 970001L;
    public static final long DELIVERY_ID = 880001L;
    public static final long USER_ID = 550001L;
    public static final long USER_PRINCIPAL_ID = 550001L;
    public static final long RESTAURANT_ID = 330001L;
    public static final long SHIPPER_ID = 440001L;
    public static final double PICKUP_LATITUDE = 10.776889;
    public static final double PICKUP_LONGITUDE = 106.700806;
    public static final double DELIVERY_LATITUDE = 10.782864;
    public static final double DELIVERY_LONGITUDE = 106.695732;
    public static final String RESTAURANT_NAME = "Bếp Nhà Mình";
    public static final String CUSTOMER_NAME = "Nguyễn Minh Anh";
    public static final String SHIPPER_NAME = "Trần Văn Bình";
    public static final String PICKUP_ADDRESS = "18 Nguyễn Huệ, phường Bến Nghé, Quận 1, TP. Hồ Chí Minh";
    public static final String DELIVERY_ADDRESS = "12 Nguyễn Thiện Thuật, phường 2, Quận 3, TP. Hồ Chí Minh";
    public static final BigDecimal SUBTOTAL_VND = new BigDecimal("125000");
    public static final BigDecimal SHIPPING_FEE_VND = new BigDecimal("18000");
    public static final BigDecimal DISCOUNT_VND = new BigDecimal("5000");
    public static final BigDecimal TOTAL_VND = new BigDecimal("138000");
    public static final Instant OCCURRED_AT = Instant.parse("2026-09-27T04:00:00Z");

    private EventFixtures() {
    }

    public static ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public static OrderCreatedEvent orderCreated() {
        return orderCreated(ORDER_ID);
    }

    public static OrderCreatedEvent orderCreated(long orderId) {
        return new OrderCreatedEvent(
                id("order.created", orderId), 2, orderId, USER_ID, USER_PRINCIPAL_ID, RESTAURANT_ID,
                "PENDING", SUBTOTAL_VND, DISCOUNT_VND, SHIPPING_FEE_VND, TOTAL_VND, "COD",
                RESTAURANT_NAME, PICKUP_ADDRESS, CUSTOMER_NAME, DELIVERY_ADDRESS,
                DELIVERY_LATITUDE, DELIVERY_LONGITUDE, PICKUP_LATITUDE, PICKUP_LONGITUDE, OCCURRED_AT);
    }

    public static OrderCancelledEvent orderCancelled() {
        return new OrderCancelledEvent(
                id("order.cancelled", ORDER_ID), 2, ORDER_ID, USER_ID, USER_PRINCIPAL_ID, RESTAURANT_ID,
                "PENDING", "CANCELLED", "Khách hàng đổi kế hoạch", "CUSTOMER", "CUSTOMER_REQUEST",
                SUBTOTAL_VND, DISCOUNT_VND, SHIPPING_FEE_VND, TOTAL_VND, "COD", OCCURRED_AT);
    }

    public static DeliveryStatusUpdatedEvent deliveryStatusUpdated() {
        return new DeliveryStatusUpdatedEvent(
                id("delivery.status-updated", DELIVERY_ID), DELIVERY_ID, ORDER_ID, SHIPPER_ID,
                "DELIVERING", "PICKED_UP", OCCURRED_AT, 10.779231, 106.698521);
    }

    public static DeliveryCompletedEvent deliveryCompleted() {
        BigDecimal restaurantCommission = new BigDecimal("25000");
        BigDecimal shippingCommission = new BigDecimal("3000");
        return new DeliveryCompletedEvent(
                id("delivery.completed", DELIVERY_ID), "DELIVERY_COMPLETED", DELIVERY_ID, ORDER_ID,
                RESTAURANT_ID, SHIPPER_ID, new BigDecimal("100000"), new BigDecimal("15000"),
                restaurantCommission, shippingCommission,
                restaurantCommission.add(shippingCommission), SHIPPING_FEE_VND, TOTAL_VND, OCCURRED_AT,
                DELIVERY_ADDRESS, "COD", RESTAURANT_NAME, CUSTOMER_NAME);
    }

    public static ShipperFoundEvent shipperFound() {
        return new ShipperFoundEvent(
                id("shipper.found", DELIVERY_ID).toString(), DELIVERY_ID, ORDER_ID,
                "4f1f5c1c-9f30-4b33-9a05-7ab8dc9db001",
                List.of(new ShipperMatchResult(SHIPPER_ID, SHIPPER_NAME, "0908123456", 1.4,
                        10.779231, 106.698521, 4.92, true)), OCCURRED_AT,
                RESTAURANT_NAME, PICKUP_ADDRESS, DELIVERY_ADDRESS);
    }

    public static ShipperNotFoundEvent shipperNotFound() {
        return new ShipperNotFoundEvent(
                id("shipper.not-found", DELIVERY_ID).toString(), DELIVERY_ID, ORDER_ID,
                "4f1f5c1c-9f30-4b33-9a05-7ab8dc9db001", "Không có tài xế phù hợp sau khi thử lại",
                OCCURRED_AT, 3, PICKUP_LATITUDE, PICKUP_LONGITUDE,
                DELIVERY_LATITUDE, DELIVERY_LONGITUDE);
    }

    public static ShipperLocationUpdatedEvent shipperLocationUpdated() {
        return new ShipperLocationUpdatedEvent(
                id("shipper.location-updated", SHIPPER_ID), SHIPPER_ID, DELIVERY_ID,
                10.779231, 106.698521, true, 1_758_912_000_000L, 8.5, 21.0, 90.0, "MOBILE_GPS");
    }

    public static IdentityStatusChangedEvent identityStatusChanged() {
        return new IdentityStatusChangedEvent(
                id("identity.status.changed", USER_PRINCIPAL_ID), "identity.status.changed", 1,
                OCCURRED_AT, USER_PRINCIPAL_ID, "ACTIVE", 3L, "EMAIL_VERIFIED", USER_PRINCIPAL_ID);
    }

    public static RestaurantOrderDecisionEvent restaurantOrderConfirmed() {
        return new RestaurantOrderDecisionEvent(
                id("restaurant.order-confirmed", ORDER_ID), "CONFIRMED", OCCURRED_AT,
                ORDER_ID, RESTAURANT_ID, 330099L, "CONFIRMED", "CONFIRM", 25, null,
                "Nhà hàng đã nhận đơn");
    }

    public static PaymentEvent paymentCompleted() {
        return new PaymentEvent(770001L, ORDER_ID, USER_ID, "COMPLETED", TOTAL_VND,
                "PAYOS", "PAYOS-970001", OCCURRED_AT, null);
    }

    public static PaymentEvent paymentFailed() {
        return new PaymentEvent(770002L, ORDER_ID, USER_ID, "FAILED", TOTAL_VND,
                "PAYOS", null, OCCURRED_AT, "Cổng thanh toán từ chối giao dịch");
    }

    public static DeliveryExceptionReportedEvent deliveryExceptionReported() {
        return new DeliveryExceptionReportedEvent(
                id("delivery.exception.reported", DELIVERY_ID), "DELIVERY_EXCEPTION_REPORTED", OCCURRED_AT,
                id("delivery.exception", DELIVERY_ID), DELIVERY_ID, ORDER_ID, USER_ID, RESTAURANT_ID,
                SHIPPER_ID, "DELIVERING", "EXCEPTION_REPORTED", "RETRY_AVAILABLE",
                "Xe gặp sự cố trên đường giao hàng", TOTAL_VND);
    }

    /** Returns one fixture for each commonly consumed event shape. */
    public static List<Object> commonEvents() {
        return List.of(orderCreated(), orderCancelled(), deliveryStatusUpdated(), deliveryCompleted(),
                shipperFound(), shipperNotFound(), shipperLocationUpdated(), identityStatusChanged(),
                restaurantOrderConfirmed(), paymentCompleted(), deliveryExceptionReported());
    }

    /** Stable names make snapshots and retry tests reproducible. */
    private static UUID id(String namespace, long aggregateId) {
        return UUID.nameUUIDFromBytes((namespace + ":" + aggregateId)
                .getBytes(StandardCharsets.UTF_8));
    }
}
