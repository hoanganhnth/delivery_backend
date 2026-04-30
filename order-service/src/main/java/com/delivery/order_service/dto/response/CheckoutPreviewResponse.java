package com.delivery.order_service.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * ✅ Response chứa giá tính toán bởi server.
 * Client hiển thị thông tin này trên màn Checkout thay vì tính giá local.
 */
@Getter
@Setter
@Builder
public class CheckoutPreviewResponse {

    private Long restaurantId;
    private String restaurantName;

    private List<PreviewItemDetail> items;

    private BigDecimal subtotal;        // Tổng tiền hàng (server tính)
    private BigDecimal shippingFee;     // Phí ship (server tính theo khoảng cách)
    private BigDecimal discountAmount;  // Giảm giá (từ coupon nếu có)
    private BigDecimal totalPrice;      // subtotal + shippingFee - discountAmount

    private String couponCode;          // Coupon đã áp dụng (null nếu không có)
    private String couponMessage;       // "Giảm 20k" hoặc "Mã không hợp lệ"

    private List<PriceChangeInfo> priceChanges;     // Danh sách giá thay đổi
    private List<Long> unavailableItemIds;           // Món đã hết hàng

    @Getter
    @Setter
    @Builder
    public static class PreviewItemDetail {
        private Long menuItemId;
        private String menuItemName;
        private String imageUrl;
        private BigDecimal unitPrice;   // Giá server canonical
        private Integer quantity;
        private BigDecimal lineTotal;   // unitPrice * quantity
    }

    @Getter
    @Setter
    @Builder
    public static class PriceChangeInfo {
        private Long menuItemId;
        private String menuItemName;
        private BigDecimal oldPrice;    // Giá client gửi lên (nếu có)
        private BigDecimal newPrice;    // Giá server canonical
    }
}
