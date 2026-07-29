package com.delivery.order_service.controller;

import com.delivery.order_service.common.constants.ApiPathConstants;
import com.delivery.order_service.common.constants.HttpHeaderConstants;
import com.delivery.order_service.common.constants.RoleConstants;
import com.delivery.order_service.dto.request.CheckoutPreviewRequest;
import com.delivery.order_service.dto.request.CancelOrderRequest;
import com.delivery.order_service.dto.request.CreateOrderRequest;
import com.delivery.order_service.dto.response.CheckoutPreviewResponse;
import com.delivery.order_service.dto.response.OrderResponse;
import com.delivery.order_service.payload.BaseResponse;
import com.delivery.order_service.payload.PageResponse;
import com.delivery.order_service.service.CheckoutPreviewService;
import com.delivery.order_service.service.OrderService;
import com.delivery.order_service.exception.ValidationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@RestController
@RequestMapping(ApiPathConstants.ORDERS)
public class OrderController {

    private final OrderService orderService;
    private final CheckoutPreviewService checkoutPreviewService;

    public OrderController(OrderService orderService, CheckoutPreviewService checkoutPreviewService) {
        this.orderService = orderService;
        this.checkoutPreviewService = checkoutPreviewService;
    }

    /**
     * ✅ Checkout Preview — Server tính toán giá chính xác trước khi đặt hàng.
     * Client gọi endpoint này khi mở màn Checkout để hiển thị breakdown.
     */
    @PostMapping("/checkout-preview")
    public ResponseEntity<BaseResponse<CheckoutPreviewResponse>> checkoutPreview(
            @Valid @RequestBody CheckoutPreviewRequest request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        requireCustomerRole(role);
        CheckoutPreviewResponse preview = checkoutPreviewService.calculatePreview(request, userId);
        return ResponseEntity.ok(new BaseResponse<>(1, preview, "Checkout preview thành công"));
    }

    @PostMapping
    public ResponseEntity<BaseResponse<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        OrderResponse response = orderService.createOrder(request, userId, role);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Tạo đơn hàng thành công"));
    }

    private void requireCustomerRole(String role) {
        if (!RoleConstants.USER.equals(role)) {
            throw new com.delivery.order_service.exception.AccessDeniedException(
                    "Chỉ khách hàng được tạo hoặc preview đơn hàng");
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<OrderResponse>> getOrderById(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        OrderResponse response = orderService.getOrderById(id, userId, role);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy thông tin đơn hàng thành công"));
    }

    @GetMapping("/my-orders")
    public ResponseEntity<BaseResponse<PageResponse<OrderResponse>>> getMyOrders(
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        requireCustomerRole(role);
        Pageable pageable = boundedPageable(page, size);
        Page<OrderResponse> response = orderService.getOrdersByUser(userId, userId, role, pageable);
        return ResponseEntity.ok(new BaseResponse<>(1, PageResponse.from(response), "Lấy danh sách đơn hàng của tôi thành công"));
    }

    @GetMapping("/my-restaurant-orders")
    public ResponseEntity<BaseResponse<PageResponse<OrderResponse>>> getMyRestaurantOrders(
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        requireRestaurantOwnerRole(role);
        Pageable pageable = boundedPageable(page, size);
        Page<OrderResponse> response = orderService.getOrdersByRestaurantOwner(userId, userId, role, pageable);
        return ResponseEntity.ok(new BaseResponse<>(1, PageResponse.from(response), "Lấy danh sách đơn hàng của nhà hàng tôi sở hữu thành công"));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<BaseResponse<PageResponse<OrderResponse>>> getOrdersByStatus(
            @PathVariable String status,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = boundedPageable(page, size);
        Page<OrderResponse> response = orderService.getOrdersByStatus(status, userId, role, pageable);
        return ResponseEntity.ok(new BaseResponse<>(1, PageResponse.from(response), "Lấy danh sách đơn hàng theo trạng thái thành công"));
    }

    @GetMapping("/all")
    public ResponseEntity<BaseResponse<PageResponse<OrderResponse>>> getAllOrders(
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = boundedPageable(page, size);
        Page<OrderResponse> response = orderService.getAllOrders(userId, role, pageable);
        return ResponseEntity.ok(new BaseResponse<>(1, PageResponse.from(response), "Lấy tất cả đơn hàng thành công"));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<BaseResponse<OrderResponse>> cancelOrder(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role,
            @Valid @RequestBody(required = false) CancelOrderRequest cancelRequest) {
        String reason = cancelRequest != null ? cancelRequest.getReason() : null;
        OrderResponse response = orderService.cancelOrder(id, userId, role, reason);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Hủy đơn hàng thành công"));
    }

    private Pageable boundedPageable(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ValidationException("Pagination requires page >= 0 and size between 1 and 100");
        }
        return PageRequest.of(page, size);
    }

    private void requireRestaurantOwnerRole(String role) {
        if (!RoleConstants.RESTAURANT_OWNER.equals(role)) {
            throw new com.delivery.order_service.exception.AccessDeniedException(
                    "Chỉ chủ nhà hàng được xem đơn hàng của nhà hàng mình");
        }
    }
}
