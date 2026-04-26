package com.delivery.order_service.service;

import com.delivery.order_service.dto.internal.ValidatedOrderData;
import com.delivery.order_service.dto.request.CreateOrderRequest;
import com.delivery.order_service.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ✅ Service validation cho Order theo Backend Instructions
 * Tích hợp với Restaurant Service để validate restaurant và menu items
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderValidationService {

    private final WebClient webClient;

    @Value("${restaurant.service.url:http://localhost:8083}")
    private String restaurantServiceUrl;

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

        // 4. Validate financial data
        validateFinancialData(request, errors);

        // 5. Validate user context
        validateUserContext(request, userId, errors);

        // 6. Validate với restaurant service — LẦN GỌI DUY NHẤT đến restaurant-service.
        // Thu canonical restaurant data (name, address, phone, lat, lng, creatorId) từ
        // server.
        ValidatedOrderData validatedData = validateWithRestaurantService(request, userId, errors);

        if (!errors.isEmpty()) {
            String errorMessage = "Dữ liệu đơn hàng không hợp lệ: " + String.join(", ", errors);
            log.error("🚨 Order validation failed for user {}: {}", userId, errorMessage);
            throw new ValidationException(errorMessage);
        }

        log.info("✅ Order validation passed for user: {}, creatorId: {}", userId,
                validatedData != null ? validatedData.creatorId() : null);
        return validatedData;
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
        } else if (!request.getPaymentMethod().matches("^(COD|ONLINE)$")) {
            errors.add("Phương thức thanh toán phải là COD hoặc ONLINE");
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
        for (int i = 0; i < request.getItems().size(); i++) {
            CreateOrderRequest.OrderItemRequest item = request.getItems().get(i);
            validateOrderItem(item, i + 1, errors);
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

        // Menu Item Name validation
        if (item.getMenuItemName() == null || item.getMenuItemName().trim().isEmpty()) {
            errors.add(prefix + "Tên sản phẩm không được để trống");
        } else if (item.getMenuItemName().length() > 255) {
            errors.add(prefix + "Tên sản phẩm không được vượt quá 255 ký tự");
        }

        // Quantity validation
        if (item.getQuantity() == null) {
            errors.add(prefix + "Số lượng không được để trống");
        } else if (item.getQuantity() <= 0) {
            errors.add(prefix + "Số lượng phải lớn hơn 0");
        } else if (item.getQuantity() > 99) {
            errors.add(prefix + "Số lượng không được vượt quá 99");
        }

        // Price validation
        if (item.getPrice() == null) {
            errors.add(prefix + "Giá sản phẩm không được để trống");
        } else if (item.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(prefix + "Giá sản phẩm phải lớn hơn 0");
        } else if (item.getPrice().compareTo(new BigDecimal("10000000")) > 0) {
            errors.add(prefix + "Giá sản phẩm không được vượt quá 10,000,000 VND");
        }

        // Notes validation (optional but with size limit)
        if (item.getNotes() != null && item.getNotes().length() > 500) {
            errors.add(prefix + "Ghi chú sản phẩm không được vượt quá 500 ký tự");
        }

        // Calculate total price for this item (only if both price and quantity are
        // valid)
        if (item.getPrice() != null && item.getQuantity() != null &&
                item.getPrice().compareTo(BigDecimal.ZERO) > 0 && item.getQuantity() > 0) {

            BigDecimal itemTotal = item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            if (itemTotal.compareTo(new BigDecimal("50000000")) > 0) {
                errors.add(prefix + "Tổng giá trị sản phẩm không được vượt quá 50,000,000 VND");
            }
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
     * Validate financial data consistency
     */
    private void validateFinancialData(CreateOrderRequest request, List<String> errors) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            return;
        }

        // Calculate total order value
        BigDecimal totalValue = request.getItems().stream()
                .filter(item -> item.getPrice() != null && item.getQuantity() != null)
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Minimum order value
        if (totalValue.compareTo(new BigDecimal("10000")) < 0) {
            errors.add("Giá trị đơn hàng tối thiểu là 10,000 VND");
        }

        // Maximum order value
        if (totalValue.compareTo(new BigDecimal("100000000")) > 0) {
            errors.add("Giá trị đơn hàng không được vượt quá 100,000,000 VND");
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
     * Calculate distance between two coordinates using Haversine formula
     */
    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        final int R = 6371; // Radius of the earth in km

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lng2 - lng1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c;

        return distance;
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
                                    "menuItemName", item.getMenuItemName(),
                                    "quantity", item.getQuantity(),
                                    "price", item.getPrice()))
                            .toList());

            String url = restaurantServiceUrl + "/api/restaurants/validate/order";

            Map<String, Object> responseBody = webClient
                    .post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .header("X-User-Id", userId.toString())
                    .bodyValue(orderValidationRequest)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

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

            ValidatedOrderData validatedData = buildValidatedOrderData(restaurantInfo);

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
    private ValidatedOrderData buildValidatedOrderData(Map<String, Object> restaurantInfo) {
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

        log.info("✅ Server-validated restaurant data: name={}, creatorId={}", name, creatorId);
        return new ValidatedOrderData(creatorId, name, address, phone, lat, lng);
    }
}
