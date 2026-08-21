package com.delivery.order_service.service;

import com.delivery.order_service.dto.internal.ValidatedOrderData;
import com.delivery.order_service.dto.request.CreateOrderRequest;
import com.delivery.order_service.exception.ValidationException;
import com.delivery.order_service.config.OrderRestaurantCircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/**
 * ✅ Service validation cho Order theo Backend Instructions
 * Tích hợp với Restaurant Service để validate restaurant và menu items
 */
@Slf4j
@Service
public class OrderValidationService {

    private final WebClient webClient;
    private final String restaurantServiceUrl;
    private final String internalSecret;
    private final OrderRestaurantCircuitBreaker restaurantCircuitBreaker;
    @Value("${app.order.voucher-checkout-enabled:false}")
    private boolean voucherCheckoutEnabled;
    @Value("${app.order.flashsale-checkout-enabled:false}")
    private boolean flashSaleCheckoutEnabled;

    public OrderValidationService(
            WebClient webClient,
            @Value("${restaurant.service.url}") String restaurantServiceUrl,
            @Value("${app.internal.secret:}") String internalSecret,
            OrderRestaurantCircuitBreaker restaurantCircuitBreaker) {
        this.webClient = webClient;
        this.restaurantServiceUrl = restaurantServiceUrl;
        this.internalSecret = internalSecret;
        this.restaurantCircuitBreaker = restaurantCircuitBreaker;
    }

    /**
     * Validate toàn bộ CreateOrderRequest.
     * Trả về ValidatedOrderData chứa thông tin nhà hàng đã được server xác thực.
     * Client KHÔNG cần gửi restaurantName, restaurantAddress, restaurantPhone,
     * pickupLat/Lng.
     */
    public ValidatedOrderData validateCreateOrderRequest(CreateOrderRequest request, Long userId) {
        List<String> errors = new ArrayList<>();

        // 1. Validate basic required fields (chỉ những gì client phải gửi)
        validateRequiredFields(request, errors);

        // 2. Validate business logic
        validateBusinessRules(request, errors);

        // 3. Validate coordinates (delivery coords mà client cung cấp)
        validateDeliveryCoordinates(request, errors);

        // 4. Validate user context. Prices are resolved by restaurant-service and
        // must never be trusted or required from the client.
        validateUserContext(request, userId, errors);

        // Không gọi service khác khi request đã sai ngay tại boundary HTTP.
        if (!errors.isEmpty()) {
            throwValidation(userId, errors);
        }

        // 6. Validate với restaurant service — LẦN GỌI DUY NHẤT đến restaurant-service.
        // Thu canonical restaurant data (name, address, phone, lat, lng, creatorId) từ
        // server.
        ValidatedOrderData validatedData = validateWithRestaurantService(request, userId, errors);

        if (!errors.isEmpty()) {
            throwValidation(userId, errors);
        }

        log.info("✅ Order validation passed for user: {}, creatorId: {}", userId,
                validatedData != null ? validatedData.creatorId() : null);
        return validatedData;
    }

    private void throwValidation(Long userId, List<String> errors) {
        String errorMessage = "Dữ liệu đơn hàng không hợp lệ: " + String.join(", ", errors);
        log.error("🚨 Order validation failed for user {}: {}", userId, errorMessage);
        throw new ValidationException(errorMessage);
    }

    /**
     * Validate required fields — CHỈ những trường mà client phải gửi.
     * Thông tin nhà hàng (name, address, phone, lat, lng) sẽ lấy từ server.
     */
    private void validateRequiredFields(CreateOrderRequest request, List<String> errors) {
        // Restaurant ID — trường DUY NHẤT phía nhà hàng mà client cung cấp
        if (request.getRestaurantId() == null) {
            errors.add("Restaurant ID không được để trống");
        } else if (request.getRestaurantId() <= 0) {
            errors.add("Restaurant ID phải là số dương");
        }

        // Delivery info
        if (request.getDeliveryAddress() == null || request.getDeliveryAddress().trim().isEmpty()) {
            errors.add("Địa chỉ giao hàng không được để trống");
        } else if (request.getDeliveryAddress().length() > 500) {
            errors.add("Địa chỉ giao hàng không được vượt quá 500 ký tự");
        }

        // Customer info
        if (request.getCustomerName() == null || request.getCustomerName().trim().isEmpty()) {
            errors.add("Tên khách hàng không được để trống");
        } else if (request.getCustomerName().length() > 100) {
            errors.add("Tên khách hàng không được vượt quá 100 ký tự");
        }

        if (request.getCustomerPhone() == null || request.getCustomerPhone().trim().isEmpty()) {
            errors.add("Số điện thoại khách hàng không được để trống");
        }

        // Payment method
        if (request.getPaymentMethod() == null || request.getPaymentMethod().trim().isEmpty()) {
            errors.add("Phương thức thanh toán không được để trống");
        } else if (!"COD".equals(request.getPaymentMethod())) {
            errors.add("MVP hiện chỉ hỗ trợ thanh toán COD");
        }

        // Notes (optional)
        if (request.getNotes() != null && request.getNotes().length() > 1000) {
            errors.add("Ghi chú không được vượt quá 1000 ký tự");
        }
    }

    /**
     * Validate business rules
     */
    private void validateBusinessRules(CreateOrderRequest request, List<String> errors) {
        int voucherCount = request.getVoucherIds() == null ? 0 : request.getVoucherIds().size();
        if (voucherCount > 1) {
            errors.add("Mỗi đơn chỉ được dùng một voucher");
        } else if (voucherCount == 1 && !voucherCheckoutEnabled) {
            errors.add("Voucher checkout chưa được mở trong MVP cho tới khi có discount và compensation proof");
        }

        // Minimum order validation
        if (request.getItems() == null || request.getItems().isEmpty()) {
            errors.add("Đơn hàng phải có ít nhất một sản phẩm");
            return;
        }

        // Maximum items per order
        if (request.getItems().size() > 50) {
            errors.add("Đơn hàng không được vượt quá 50 sản phẩm");
        }

        // Validate each item
        Set<Long> menuItemIds = new HashSet<>();
        for (int i = 0; i < request.getItems().size(); i++) {
            CreateOrderRequest.OrderItemRequest item = request.getItems().get(i);
            validateOrderItem(item, i + 1, errors);
            if (item.getMenuItemId() != null && !menuItemIds.add(item.getMenuItemId())) {
                errors.add("Sản phẩm " + (i + 1) + ": Menu Item ID bị trùng");
            }
            if (item.getFlashSaleItemId() != null && !flashSaleCheckoutEnabled) {
                errors.add("Sản phẩm " + (i + 1)
                        + ": Flash Sale checkout chưa được mở trong MVP cho tới khi reservation có idempotency/compensation proof");
            }
        }

        boolean hasFlashSale = request.getItems().stream().anyMatch(item -> item.getFlashSaleItemId() != null);
        if (voucherCount == 1 && hasFlashSale) {
            errors.add("Voucher và Flash Sale không được áp dụng cùng một đơn");
        }

        // Phone number format validation (more strict)
        if (request.getCustomerPhone() != null && !isValidVietnamesePhoneNumber(request.getCustomerPhone())) {
            errors.add("Số điện thoại khách hàng không đúng định dạng Việt Nam");
        }
    }

    /**
     * Validate individual order item
     */
    private void validateOrderItem(CreateOrderRequest.OrderItemRequest item, int itemIndex, List<String> errors) {
        String prefix = "Sản phẩm " + itemIndex + ": ";

        // Menu Item ID validation
        if (item.getMenuItemId() == null) {
            errors.add(prefix + "Menu Item ID không được để trống");
        } else if (item.getMenuItemId() <= 0) {
            errors.add(prefix + "Menu Item ID phải là số dương");
        }

        // Quantity validation
        if (item.getQuantity() == null) {
            errors.add(prefix + "Số lượng không được để trống");
        } else if (item.getQuantity() <= 0) {
            errors.add(prefix + "Số lượng phải lớn hơn 0");
        } else if (item.getQuantity() > 99) {
            errors.add(prefix + "Số lượng không được vượt quá 99");
        }

        // Notes validation (optional but with size limit)
        if (item.getNotes() != null && item.getNotes().length() > 500) {
            errors.add(prefix + "Ghi chú sản phẩm không được vượt quá 500 ký tự");
        }
    }

    /**
     * Validate delivery coordinates (client-provided) cho Vietnam region.
     * Pickup coords (restaurant) sẽ lấy từ server, không validate ở đây.
     */
    private void validateDeliveryCoordinates(CreateOrderRequest request, List<String> errors) {
        double MIN_LAT = 8.0, MAX_LAT = 24.0;
        double MIN_LNG = 102.0, MAX_LNG = 110.0;

        if (request.getDeliveryLat() == null || request.getDeliveryLng() == null) {
            errors.add("Tọa độ giao hàng (latitude và longitude) là bắt buộc");
        } else {
            if (request.getDeliveryLat() < MIN_LAT || request.getDeliveryLat() > MAX_LAT) {
                errors.add("Tọa độ giao hàng (latitude) phải trong phạm vi Việt Nam (8.0 - 24.0)");
            }
            if (request.getDeliveryLng() < MIN_LNG || request.getDeliveryLng() > MAX_LNG) {
                errors.add("Tọa độ giao hàng (longitude) phải trong phạm vi Việt Nam (102.0 - 110.0)");
            }
        }
    }

    /**
     * Validate user context and permissions
     */
    private void validateUserContext(CreateOrderRequest request, Long userId, List<String> errors) {
        if (userId == null || userId <= 0) {
            errors.add("User ID không hợp lệ");
        }

        // Additional business rules can be added here
        // E.g., user credit limit, delivery zone restrictions, etc.
    }

    /**
     * Validate Vietnamese phone number format
     */
    private boolean isValidVietnamesePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return false;
        }

        // Vietnamese phone number patterns
        String cleanPhone = phoneNumber.replaceAll("\\s+", "");
        return cleanPhone.matches("^(\\+84|84|0)(3|5|7|8|9)[0-9]{8}$");
    }

    /**
     * Validate với restaurant service — LẦN GỌI DUY NHẤT đến restaurant-service
     * trong luồng createOrder.
     * Trả về ValidatedOrderData chứa canonical restaurant data từ server.
     */
    @SuppressWarnings("unchecked")
    private ValidatedOrderData validateWithRestaurantService(CreateOrderRequest request, Long userId,
            List<String> errors) {
        try {
            // Chỉ gửi restaurantId và items — không gửi bất kỳ thông tin nhà hàng nào từ
            // client
            Map<String, Object> orderValidationRequest = Map.of(
                    "restaurantId", request.getRestaurantId(),
                    "items", request.getItems().stream()
                            .map(item -> Map.of(
                                    "menuItemId", item.getMenuItemId(),
                                    "quantity", item.getQuantity()))
                            .toList());

            String url = restaurantServiceUrl + "/api/restaurants/validate/order";

            if (internalSecret == null || internalSecret.isBlank()) {
                errors.add("Order/restaurant internal credential chưa được cấu hình");
                return null;
            }

            Map<String, Object> responseBody = restaurantCircuitBreaker.execute(() -> webClient
                    .post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .header("Internal-Token", internalSecret)
                    .bodyValue(orderValidationRequest)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(restaurantCircuitBreaker.timeout())
                    .block());

            if (responseBody == null) {
                errors.add("Không thể xác thực đơn hàng với restaurant service");
                return null;
            }

            Integer status = (Integer) responseBody.get("status");
            Map<String, Object> data = (Map<String, Object>) responseBody.get("data");

            log.info("🔍 Order validation for restaurant {}: status={}", request.getRestaurantId(), status);

            if (data == null) {
                errors.add("Response từ restaurant service không hợp lệ");
                return null;
            }

            // Lấy canonical restaurant data từ server
            Map<String, Object> restaurantInfo = (Map<String, Object>) data.get("restaurantInfo");
            if (restaurantInfo == null) {
                log.warn("⚠️ Restaurant service did not return restaurantInfo for: {}", request.getRestaurantId());
                errors.add("Không tìm thấy thông tin nhà hàng. Restaurant ID: " + request.getRestaurantId());
                return null;
            }

            ValidatedOrderData validatedData = buildValidatedOrderData(restaurantInfo, data, errors);

            // Thu thập errors từ validation items
            if (status != null && status != 1) {
                List<Map<String, Object>> validationErrors = (List<Map<String, Object>>) data.get("errors");
                if (validationErrors != null && !validationErrors.isEmpty()) {
                    for (Map<String, Object> error : validationErrors) {
                        String message = (String) error.get("message");
                        if (message != null)
                            errors.add(message);
                    }
                } else {
                    errors.add("Restaurant/menu item validation thất bại");
                }
            }

            return validatedData;

        } catch (Exception e) {
            log.error("💥 Error validating order with restaurant service: {}", e.getMessage());
            errors.add("Không thể xác thực thông tin restaurant/menu items");
            return null;
        }
    }

    /**
     * Build ValidatedOrderData từ restaurantInfo map trả về bởi restaurant-service.
     * Không có side-effect, không chạm vào request.
     */
    @SuppressWarnings("unchecked")
    private ValidatedOrderData buildValidatedOrderData(
            Map<String, Object> restaurantInfo,
            Map<String, Object> validationData,
            List<String> errors) {
        String name = restaurantInfo.get("restaurantName") != null
                ? restaurantInfo.get("restaurantName").toString()
                : null;
        String address = restaurantInfo.get("restaurantAddress") != null
                ? restaurantInfo.get("restaurantAddress").toString()
                : null;
        String phone = restaurantInfo.get("restaurantPhone") != null
                ? restaurantInfo.get("restaurantPhone").toString()
                : null;

        Double lat = null;
        if (restaurantInfo.get("latitude") != null) {
            try {
                lat = Double.valueOf(restaurantInfo.get("latitude").toString());
            } catch (NumberFormatException e) {
                log.warn("⚠️ Invalid latitude: {}", restaurantInfo.get("latitude"));
            }
        }

        Double lng = null;
        if (restaurantInfo.get("longitude") != null) {
            try {
                lng = Double.valueOf(restaurantInfo.get("longitude").toString());
            } catch (NumberFormatException e) {
                log.warn("⚠️ Invalid longitude: {}", restaurantInfo.get("longitude"));
            }
        }

        Long creatorId = null;
        if (restaurantInfo.get("creatorId") != null) {
            try {
                creatorId = Long.valueOf(restaurantInfo.get("creatorId").toString());
            } catch (NumberFormatException e) {
                log.warn("⚠️ Invalid creatorId: {}", restaurantInfo.get("creatorId"));
            }
        }

        Long creatorPrincipalId = null;
        if (restaurantInfo.get("ownerPrincipalId") != null) {
            try {
                creatorPrincipalId = Long.valueOf(restaurantInfo.get("ownerPrincipalId").toString());
            } catch (NumberFormatException e) {
                log.warn("⚠️ Invalid ownerPrincipalId: {}", restaurantInfo.get("ownerPrincipalId"));
            }
        }

        if (name == null || name.isBlank()) {
            errors.add("Restaurant service thiếu tên nhà hàng canonical");
        }
        if (address == null || address.isBlank()) {
            errors.add("Restaurant service thiếu địa chỉ nhà hàng canonical");
        }
        if (creatorId == null || creatorId <= 0) {
            errors.add("Restaurant service thiếu owner ID canonical");
        }
        if (!isFiniteInRange(lat, 8.0, 24.0) || !isFiniteInRange(lng, 102.0, 110.0)) {
            errors.add("Restaurant service thiếu tọa độ nhà hàng canonical trong phạm vi Việt Nam");
        }

        List<ValidatedOrderData.ValidatedItemData> items = new ArrayList<>();
        Object rawItems = validationData.get("itemValidations");
        if (rawItems instanceof List<?> itemValidations) {
            for (Object rawItem : itemValidations) {
                if (!(rawItem instanceof Map<?, ?> item)) {
                    errors.add("Restaurant service trả item validation không hợp lệ");
                    continue;
                }
                try {
                    Long menuItemId = Long.valueOf(item.get("menuItemId").toString());
                    String menuItemName = item.get("menuItemName") != null
                            ? item.get("menuItemName").toString() : null;
                    BigDecimal price = item.get("actualPrice") != null
                            ? new BigDecimal(item.get("actualPrice").toString()) : null;
                    if (menuItemName == null || menuItemName.isBlank()
                            || price == null || price.signum() <= 0) {
                        errors.add("Restaurant service thiếu dữ liệu canonical cho món " + menuItemId);
                    } else {
                        items.add(new ValidatedOrderData.ValidatedItemData(
                                menuItemId, menuItemName, price));
                    }
                } catch (RuntimeException e) {
                    errors.add("Restaurant service trả item validation không hợp lệ");
                }
            }
        } else {
            errors.add("Restaurant service không trả dữ liệu canonical của món ăn");
        }

        log.info("✅ Server-validated restaurant data: name={}, creatorId={}, items={}",
                name, creatorId, items.size());
        return new ValidatedOrderData(creatorId, creatorPrincipalId, name, address, phone, lat, lng,
                List.copyOf(items));
    }

    private boolean isFiniteInRange(Double value, double min, double max) {
        return value != null && Double.isFinite(value) && value >= min && value <= max;
    }
}
