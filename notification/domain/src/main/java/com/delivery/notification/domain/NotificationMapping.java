package com.delivery.notification.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Existing transactional event mapping; does not consult marketing preferences. */
public final class NotificationMapping {
    private NotificationMapping() {}

    public static NotificationIntent orderCreated(UUID eventId, Long userId, Long userPrincipalId,
            Long orderId, String restaurantName) {
        requireEventId(eventId);
        requirePositiveId(userId, "userId");
        requirePositiveId(orderId, "orderId");
        if (restaurantName == null || restaurantName.isBlank()) {
            throw new IllegalArgumentException("canonical restaurantName is required");
        }
        Draft request = new Draft();
        request.userId = userId;
        request.userPrincipalId = userPrincipalId;
        request.title = "Đơn hàng đã được tạo";
        request.message = "Đơn hàng #" + orderId + " từ " + restaurantName + " đã được tạo thành công";
        request.type = Types.ORDER_CREATED;
        request.priority = Types.PRIORITY_MEDIUM;
        request.relatedEntityId = orderId;
        request.relatedEntityType = "ORDER";
        request.deduplicationKey = "order-created:" + eventId;

        return request.build(null);
    }

    public static NotificationIntent deliveryStatus(UUID eventId, Long userId, Long userPrincipalId,
            Long deliveryId, String status, String shipperName) {
        requireEventId(eventId);
        requirePositiveId(userId, "userId");
        requirePositiveId(deliveryId, "deliveryId");
        String title = getDeliveryStatusTitle(status);
        String message = getDeliveryStatusMessage(status, shipperName);

        Draft request = new Draft();
        request.userId = userId;
        request.userPrincipalId = userPrincipalId;
        request.title = title;
        request.message = message;
        request.type = getDeliveryStatusType(status);
        request.priority = Types.PRIORITY_HIGH;
        request.relatedEntityId = deliveryId;
        request.relatedEntityType = "DELIVERY";
        request.deduplicationKey = "delivery-status:" + eventId;

        return request.build(null);
    }

    public static NotificationIntent shipperOffer(Long shipperId, Long orderId, String restaurantName,
            String pickupAddress, String deliveryAddress, Double distance, String offerEventId) {
        requirePositiveId(shipperId, "shipperId");
        requirePositiveId(orderId, "orderId");
        Draft request = new Draft();
        request.userId = shipperId;
        request.title = "🎯 Đơn hàng phù hợp!";
        request.message = String.format(
                "Đơn hàng #%d từ %s - cách điểm lấy khoảng %.1fkm. Mở ứng dụng để xem offer hiện tại.",
                orderId, restaurantName, distance);
        request.type = Types.MATCH_FOUND;
        request.priority = Types.PRIORITY_HIGH;
        request.relatedEntityId = orderId;
        request.relatedEntityType = "ORDER";
        request.deduplicationKey = "shipper-offer:" + offerEventId + ":" + shipperId;
        // Persist the inbox record and use FCM only as a best-effort wake-up;
        // Delivery's authenticated current-offer endpoint is the source of truth.
        request.sendPush = true;
        // Add detailed info to data field
        Map<String, Object> data = new HashMap<>();
        data.put("pickupAddress", pickupAddress);
        data.put("deliveryAddress", deliveryAddress);
        data.put("distance", distance);
        data.put("orderId", orderId);
        data.put("recoveryEndpoint", "/api/deliveries/offers/current");
        return request.build(data);
    }

    private static void requirePositiveId(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }
    private static void requireEventId(UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("stable eventId is required");
        }
    }

    private static String getDeliveryStatusTitle(String status) {
        return switch (status) {
            case "PENDING" -> "Đơn đang chờ xử lý giao hàng";
            case "FINDING_SHIPPER" -> "Đang tìm shipper";
            case "WAIT_SHIPPER_CONFIRM" -> "Đang chờ shipper xác nhận";
            case "SHIPPER_NOT_FOUND" -> "Chưa tìm được shipper";
            case "ASSIGNED" -> "Đã phân công shipper";
            case "PICKED_UP" -> "Shipper đã lấy hàng";
            case "DELIVERING" -> "Đơn hàng đang được giao";
            case "DELIVERED" -> "Giao hàng hoàn thành";
            case "CANCELLED" -> "Giao hàng đã bị hủy";
            default -> throw new IllegalArgumentException("Unknown delivery status: " + status);
        };
    }

    private static String getDeliveryStatusMessage(String status, String shipperName) {
        return switch (status) {
            case "PENDING" -> "Đơn hàng đang chờ bắt đầu quy trình giao";
            case "FINDING_SHIPPER" -> "Hệ thống đang tìm shipper cho đơn hàng của bạn";
            case "WAIT_SHIPPER_CONFIRM" -> "Đang chờ shipper xác nhận nhận đơn";
            case "SHIPPER_NOT_FOUND" -> "Hiện chưa tìm được shipper phù hợp cho đơn hàng";
            case "ASSIGNED" -> hasText(shipperName)
                    ? shipperName + " đã được phân công giao đơn hàng của bạn"
                    : "Đơn hàng của bạn đã được phân công cho shipper";
            case "PICKED_UP" -> hasText(shipperName)
                    ? shipperName + " đã lấy đơn hàng và chuẩn bị giao"
                    : "Đơn hàng của bạn đã được lấy và chuẩn bị giao";
            case "DELIVERING" -> hasText(shipperName)
                    ? shipperName + " đang trên đường giao hàng"
                    : "Đơn hàng của bạn đang được giao";
            case "DELIVERED" -> hasText(shipperName)
                    ? "Đơn hàng đã được " + shipperName + " giao thành công"
                    : "Đơn hàng đã được giao thành công";
            case "CANCELLED" -> "Quy trình giao hàng đã bị hủy";
            default -> throw new IllegalArgumentException("Unknown delivery status: " + status);
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String getDeliveryStatusType(String status) {
        return switch (status) {
            case "PENDING" -> Types.DELIVERY_PENDING;
            case "FINDING_SHIPPER" -> Types.DELIVERY_FINDING_SHIPPER;
            case "WAIT_SHIPPER_CONFIRM" -> Types.DELIVERY_WAIT_SHIPPER_CONFIRM;
            case "SHIPPER_NOT_FOUND" -> Types.DELIVERY_SHIPPER_NOT_FOUND;
            case "ASSIGNED" -> Types.DELIVERY_ASSIGNED;
            case "PICKED_UP" -> Types.DELIVERY_PICKED_UP;
            case "DELIVERING" -> Types.DELIVERY_DELIVERING;
            case "DELIVERED" -> Types.DELIVERY_DELIVERED;
            case "CANCELLED" -> Types.DELIVERY_CANCELLED;
            default -> throw new IllegalArgumentException("Unknown delivery status: " + status);
        };
    }
    private static final class Draft {
        Long userId;
        Long userPrincipalId;
        String title;
        String message;
        String type;
        String priority;
        Long relatedEntityId;
        String relatedEntityType;
        String deduplicationKey;
        boolean sendPush = true;

        NotificationIntent build(Map<String, Object> data) {
            return new NotificationIntent(userId, userPrincipalId, title, message, type, priority,
                    relatedEntityId, relatedEntityType, deduplicationKey, sendPush, data);
        }
    }
}
