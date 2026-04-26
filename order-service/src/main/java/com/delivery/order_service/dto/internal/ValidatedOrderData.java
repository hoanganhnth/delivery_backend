package com.delivery.order_service.dto.internal;

/**
 * Dữ liệu nhà hàng đã được restaurant-service xác thực.
 * Tất cả các trường đều lấy từ server (Redis cache), không tin tưởng client.
 */
public record ValidatedOrderData(
                Long creatorId,
                String restaurantName,
                String restaurantAddress,
                String restaurantPhone,
                Double pickupLat,
                Double pickupLng) {
}
