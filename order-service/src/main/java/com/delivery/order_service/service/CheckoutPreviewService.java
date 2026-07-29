package com.delivery.order_service.service;

import com.delivery.order_service.dto.request.CheckoutPreviewRequest;
import com.delivery.order_service.dto.response.CheckoutPreviewResponse;
import com.delivery.order_service.dto.response.CheckoutPreviewResponse.PreviewItemDetail;
import com.delivery.order_service.dto.response.CheckoutPreviewResponse.PriceChangeInfo;
import com.delivery.order_service.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
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
    private final String restaurantServiceUrl;
    private final String internalSecret;

    public CheckoutPreviewService(WebClient webClient,
                                  ShippingFeeCalculationService shippingFeeService,
                                  @Value("${restaurant.service.url}") String restaurantServiceUrl,
                                  @Value("${app.internal.secret:}") String internalSecret) {
        this.webClient = webClient;
        this.shippingFeeService = shippingFeeService;
        this.restaurantServiceUrl = restaurantServiceUrl;
        this.internalSecret = internalSecret;
    }

    /**
     * Tính toán checkout preview.
     * 1. Gọi restaurant-service lấy menu items + restaurant info
     * 2. Tính subtotal từ giá server
     * 3. Tính shipping fee theo khoảng cách
     * 4. Giữ discount bằng 0 trong COD MVP
     * 5. Trả về breakdown chi tiết
     */
    @SuppressWarnings("unchecked")
    public CheckoutPreviewResponse calculatePreview(CheckoutPreviewRequest request, Long userId) {
        log.info("📋 Calculating checkout preview for user={}, restaurant={}", userId, request.getRestaurantId());

        // COD MVP does not expose discount calculation or voucher reservation.
        // Reject the capability at the boundary instead of silently returning a
        // preview whose coupon is echoed back with a zero discount.
        if (request.getCouponCode() != null && !request.getCouponCode().isBlank()) {
            throw new ValidationException(
                    "Voucher checkout chưa được mở trong MVP cho tới khi có discount và compensation proof");
        }

        validateDuplicateItems(request);

        // 1. Lấy canonical restaurant + menu item facts qua cùng internal
        // validation contract với create-order. Preview không được gọi public
        // catalog endpoint rồi tự suy luận vì path đó yếu hơn checkout boundary.
        Map<String, Object> validationData = fetchValidatedCheckoutFacts(request);
        Map<String, Object> restaurantInfo = requireMap(
                validationData.get("restaurantInfo"),
                "Restaurant service không trả restaurantInfo canonical");

        String restaurantName = requireNonBlankString(
                restaurantInfo.get("restaurantName"),
                "Restaurant service thiếu tên nhà hàng canonical");
        Double pickupLat = requireCoordinate(
                restaurantInfo.get("latitude"),
                8.0,
                24.0,
                "Restaurant service thiếu tọa độ pickup latitude canonical");
        Double pickupLng = requireCoordinate(
                restaurantInfo.get("longitude"),
                102.0,
                110.0,
                "Restaurant service thiếu tọa độ pickup longitude canonical");

        Map<Long, ValidatedPreviewItem> menuItemMap = parseValidatedItems(validationData);

        // 3. Map từng item trong request → giá server
        List<PreviewItemDetail> previewItems = new ArrayList<>();
        List<PriceChangeInfo> priceChanges = new ArrayList<>();
        List<Long> unavailableIds = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (CheckoutPreviewRequest.PreviewItem reqItem : request.getItems()) {
            ValidatedPreviewItem serverItem = menuItemMap.get(reqItem.getMenuItemId());

            if (serverItem == null) {
                unavailableIds.add(reqItem.getMenuItemId());
                continue;
            }

            if (!serverItem.available()) {
                unavailableIds.add(reqItem.getMenuItemId());
                continue;
            }

            BigDecimal lineTotal = serverItem.price().multiply(BigDecimal.valueOf(reqItem.getQuantity()));
            subtotal = subtotal.add(lineTotal);

            previewItems.add(PreviewItemDetail.builder()
                    .menuItemId(reqItem.getMenuItemId())
                    .menuItemName(serverItem.name())
                    .imageUrl(null)
                    .unitPrice(serverItem.price())
                    .quantity(reqItem.getQuantity())
                    .lineTotal(lineTotal)
                    .build());
        }

        if (!unavailableIds.isEmpty()) {
            throw new ValidationException("Checkout chứa món không khả dụng: " + unavailableIds);
        }

        // 4. Tính shipping fee
        BigDecimal shippingFee = shippingFeeService.calculateShippingFee(
                pickupLat, pickupLng,
                request.getDeliveryLat(), request.getDeliveryLng(),
                subtotal);

        // 5. Discount remains zero because coupon input was rejected at the boundary.
        BigDecimal discountAmount = BigDecimal.ZERO;

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
                .couponMessage(null)
                .priceChanges(priceChanges)
                .unavailableItemIds(unavailableIds)
                .build();
    }

    // ────────────────── Private helpers ──────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchValidatedCheckoutFacts(CheckoutPreviewRequest request) {
        if (internalSecret == null || internalSecret.isBlank()) {
            throw new ValidationException("Order/restaurant internal credential chưa được cấu hình");
        }

        try {
            Map<String, Object> orderValidationRequest = Map.of(
                    "restaurantId", request.getRestaurantId(),
                    "items", request.getItems().stream()
                            .map(item -> Map.of(
                                    "menuItemId", item.getMenuItemId(),
                                    "quantity", item.getQuantity()))
                            .toList());

            Map<String, Object> response = webClient
                    .post()
                    .uri(restaurantServiceUrl + "/api/restaurants/validate/order")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Internal-Token", internalSecret)
                    .bodyValue(orderValidationRequest)
                    .retrieve().bodyToMono(Map.class).block();

            if (response == null) {
                throw new ValidationException("Không thể xác thực checkout với restaurant service");
            }
            Map<String, Object> data = requireMap(response.get("data"),
                    "Response từ restaurant service không hợp lệ");
            Integer status = getIntegerValue(response.get("status"));
            if (status == null || status != 1) {
                throw new ValidationException("Dữ liệu checkout không hợp lệ: "
                        + validationMessage(data));
            }

            return data;
        } catch (Exception e) {
            if (e instanceof ValidationException validationException) {
                throw validationException;
            }
            log.error("❌ Failed to validate checkout for restaurant {}: {}",
                    request.getRestaurantId(), e.getMessage());
            throw new ValidationException("Không thể xác thực thông tin restaurant/menu items");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Long, ValidatedPreviewItem> parseValidatedItems(Map<String, Object> validationData) {
        Object rawItems = validationData.get("itemValidations");
        if (!(rawItems instanceof List<?> itemValidations)) {
            throw new ValidationException("Restaurant service không trả dữ liệu canonical của món ăn");
        }

        Map<Long, ValidatedPreviewItem> items = new HashMap<>();
        for (Object rawItem : itemValidations) {
            if (!(rawItem instanceof Map<?, ?> item)) {
                throw new ValidationException("Restaurant service trả item validation không hợp lệ");
            }
            Long menuItemId = getLongValue(item.get("menuItemId"));
            String name = getStringValue(item.get("menuItemName"));
            BigDecimal price = getBigDecimalValue(item.get("actualPrice"));
            boolean available = Boolean.TRUE.equals(getBooleanValue(item.get("isAvailable")))
                    && !Boolean.FALSE.equals(getBooleanValue(item.get("hasEnoughStock")))
                    && name != null
                    && !name.isBlank()
                    && price != null
                    && price.signum() > 0;
            if (menuItemId != null) {
                items.put(menuItemId, new ValidatedPreviewItem(menuItemId, name, price, available));
            }
        }
        return items;
    }

    private void validateDuplicateItems(CheckoutPreviewRequest request) {
        Set<Long> menuItemIds = new HashSet<>();
        for (CheckoutPreviewRequest.PreviewItem item : request.getItems()) {
            if (!menuItemIds.add(item.getMenuItemId())) {
                throw new ValidationException("Menu Item ID bị trùng trong checkout preview");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requireMap(Object value, String message) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new ValidationException(message);
        }
        return (Map<String, Object>) map;
    }

    private String requireNonBlankString(Object value, String message) {
        String result = getStringValue(value);
        if (result == null || result.isBlank()) {
            throw new ValidationException(message);
        }
        return result;
    }

    private Double requireCoordinate(Object value, double min, double max, String message) {
        Double result = getDoubleValue(value);
        if (result == null || !Double.isFinite(result) || result < min || result > max) {
            throw new ValidationException(message);
        }
        return result;
    }

    private String validationMessage(Map<String, Object> data) {
        Object errors = data.get("errors");
        if (!(errors instanceof List<?> validationErrors) || validationErrors.isEmpty()) {
            return "Restaurant/menu item validation thất bại";
        }
        List<String> messages = new ArrayList<>();
        for (Object rawError : validationErrors) {
            if (rawError instanceof Map<?, ?> error) {
                String message = getStringValue(error.get("message"));
                if (message != null && !message.isBlank()) {
                    messages.add(message);
                }
            }
        }
        return messages.isEmpty()
                ? "Restaurant/menu item validation thất bại"
                : String.join(", ", messages);
    }

    private String getStringValue(Object val) {
        return val != null ? val.toString() : null;
    }

    private Double getDoubleValue(Object val) {
        if (val == null) return null;
        try { return Double.valueOf(val.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    private Integer getIntegerValue(Object val) {
        if (val == null) return null;
        try { return Integer.valueOf(val.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    private Long getLongValue(Object val) {
        if (val == null) return null;
        try { return Long.valueOf(val.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    private BigDecimal getBigDecimalValue(Object val) {
        if (val == null) return null;
        try { return new BigDecimal(val.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    private Boolean getBooleanValue(Object val) {
        if (val == null) return null;
        if (val instanceof Boolean bool) return bool;
        return Boolean.valueOf(val.toString());
    }

    private record ValidatedPreviewItem(
            Long menuItemId,
            String name,
            BigDecimal price,
            boolean available) {
    }
}
