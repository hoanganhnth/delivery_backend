package com.delivery.order_service.service;

import com.delivery.order_service.dto.request.CheckoutPreviewRequest;
import com.delivery.order_service.dto.response.CheckoutPreviewResponse;
import com.delivery.order_service.dto.response.CheckoutPreviewResponse.PreviewItemDetail;
import com.delivery.order_service.dto.response.CheckoutPreviewResponse.PriceChangeInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.*;

/**
 * ✅ Service tính toán checkout preview — server là nguồn giá duy nhất.
 * Gọi restaurant-service để lấy giá canonical, tính shipping fee, áp coupon.
 */
@Slf4j
@Service
public class CheckoutPreviewService {

    private final WebClient webClient;
    private final ShippingFeeCalculationService shippingFeeService;

    @Value("${restaurant.service.url:http://localhost:8083}")
    private String restaurantServiceUrl;

    public CheckoutPreviewService(WebClient webClient,
                                   ShippingFeeCalculationService shippingFeeService) {
        this.webClient = webClient;
        this.shippingFeeService = shippingFeeService;
    }

    /**
     * Tính toán checkout preview.
     * 1. Gọi restaurant-service lấy menu items + restaurant info
     * 2. Tính subtotal từ giá server
     * 3. Tính shipping fee theo khoảng cách
     * 4. Áp coupon nếu có
     * 5. Trả về breakdown chi tiết
     */
    @SuppressWarnings("unchecked")
    public CheckoutPreviewResponse calculatePreview(CheckoutPreviewRequest request, Long userId) {
        log.info("📋 Calculating checkout preview for user={}, restaurant={}", userId, request.getRestaurantId());

        // 1. Lấy thông tin nhà hàng + menu items từ restaurant-service
        Map<String, Object> restaurantData = fetchRestaurantWithMenuItems(request.getRestaurantId());
        if (restaurantData == null) {
            throw new RuntimeException("Không thể lấy thông tin nhà hàng. Restaurant ID: " + request.getRestaurantId());
        }

        String restaurantName = getStringValue(restaurantData, "name");
        Double pickupLat = getDoubleValue(restaurantData, "latitude");
        Double pickupLng = getDoubleValue(restaurantData, "longitude");

        // 2. Lấy danh sách menu items
        List<Map<String, Object>> menuItems = fetchMenuItemsByRestaurant(request.getRestaurantId());
        Map<Long, Map<String, Object>> menuItemMap = new HashMap<>();
        for (Map<String, Object> item : menuItems) {
            Long id = getLongValue(item, "id");
            if (id != null) menuItemMap.put(id, item);
        }

        // 3. Map từng item trong request → giá server
        List<PreviewItemDetail> previewItems = new ArrayList<>();
        List<PriceChangeInfo> priceChanges = new ArrayList<>();
        List<Long> unavailableIds = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (CheckoutPreviewRequest.PreviewItem reqItem : request.getItems()) {
            Map<String, Object> serverItem = menuItemMap.get(reqItem.getMenuItemId());

            if (serverItem == null) {
                unavailableIds.add(reqItem.getMenuItemId());
                continue;
            }

            String status = getStringValue(serverItem, "status");
            if (status != null && !"AVAILABLE".equalsIgnoreCase(status)) {
                unavailableIds.add(reqItem.getMenuItemId());
                continue;
            }

            BigDecimal serverPrice = getBigDecimalValue(serverItem, "price");
            if (serverPrice == null) {
                unavailableIds.add(reqItem.getMenuItemId());
                continue;
            }

            String itemName = getStringValue(serverItem, "name");
            String imageUrl = getStringValue(serverItem, "imageUrl");
            BigDecimal lineTotal = serverPrice.multiply(BigDecimal.valueOf(reqItem.getQuantity()));
            subtotal = subtotal.add(lineTotal);

            previewItems.add(PreviewItemDetail.builder()
                    .menuItemId(reqItem.getMenuItemId())
                    .menuItemName(itemName)
                    .imageUrl(imageUrl)
                    .unitPrice(serverPrice)
                    .quantity(reqItem.getQuantity())
                    .lineTotal(lineTotal)
                    .build());
        }

        // 4. Tính shipping fee
        BigDecimal shippingFee = BigDecimal.ZERO;
        if (pickupLat != null && pickupLng != null
                && request.getDeliveryLat() != null && request.getDeliveryLng() != null) {
            shippingFee = shippingFeeService.calculateShippingFee(
                    pickupLat, pickupLng,
                    request.getDeliveryLat(), request.getDeliveryLng(),
                    subtotal);
        }

        // 5. Áp coupon (placeholder — sẽ mở rộng khi có promotion-service)
        BigDecimal discountAmount = BigDecimal.ZERO;
        String couponMessage = null;
        if (request.getCouponCode() != null && !request.getCouponCode().isBlank()) {
            // TODO: Gọi promotion-service để validate coupon
            couponMessage = "Chức năng mã giảm giá đang được phát triển";
        }

        BigDecimal totalPrice = subtotal.add(shippingFee).subtract(discountAmount);

        log.info("✅ Checkout preview: subtotal={}, shipping={}, discount={}, total={}, items={}, unavailable={}",
                subtotal, shippingFee, discountAmount, totalPrice, previewItems.size(), unavailableIds.size());

        return CheckoutPreviewResponse.builder()
                .restaurantId(request.getRestaurantId())
                .restaurantName(restaurantName)
                .items(previewItems)
                .subtotal(subtotal)
                .shippingFee(shippingFee)
                .discountAmount(discountAmount)
                .totalPrice(totalPrice)
                .couponCode(request.getCouponCode())
                .couponMessage(couponMessage)
                .priceChanges(priceChanges)
                .unavailableItemIds(unavailableIds)
                .build();
    }

    // ────────────────── Private helpers ──────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchRestaurantWithMenuItems(Long restaurantId) {
        try {
            String url = restaurantServiceUrl + "/api/restaurants/" + restaurantId;
            Map<String, Object> response = webClient.get().uri(url)
                    .retrieve().bodyToMono(Map.class).block();
            if (response != null && response.get("data") != null) {
                return (Map<String, Object>) response.get("data");
            }
            return null;
        } catch (Exception e) {
            log.error("❌ Failed to fetch restaurant {}: {}", restaurantId, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchMenuItemsByRestaurant(Long restaurantId) {
        try {
            String url = restaurantServiceUrl + "/api/menu-items/restaurant/" + restaurantId + "/available";
            Map<String, Object> response = webClient.get().uri(url)
                    .retrieve().bodyToMono(Map.class).block();
            if (response != null && response.get("data") != null) {
                return (List<Map<String, Object>>) response.get("data");
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("❌ Failed to fetch menu items for restaurant {}: {}", restaurantId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private String getStringValue(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }

    private Double getDoubleValue(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        try { return Double.valueOf(val.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    private Long getLongValue(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        try { return Long.valueOf(val.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    private BigDecimal getBigDecimalValue(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        try { return new BigDecimal(val.toString()); }
        catch (NumberFormatException e) { return null; }
    }
}
