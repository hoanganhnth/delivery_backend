package com.delivery.order_service.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * ✅ Request để lấy checkout preview — server tính toán giá chính xác.
 * Client gửi danh sách items + delivery coords, server trả về giá canonical.
 */
@Getter
@Setter
public class CheckoutPreviewRequest {
    private Long restaurantId;
    private Double deliveryLat;
    private Double deliveryLng;
    private String couponCode; // Nullable — dùng khi áp mã giảm giá
    private List<PreviewItem> items;

    @Getter
    @Setter
    public static class PreviewItem {
        private Long menuItemId;
        private Integer quantity;
    }
}
