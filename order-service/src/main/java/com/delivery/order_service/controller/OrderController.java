package com.delivery.order_service.controller;

import com.delivery.order_service.common.constants.ApiPathConstants;
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
import com.delivery.auth.resourceserver.security.AuthenticatedActor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    @PostMapping("/checkout-preview")
    public ResponseEntity<BaseResponse<CheckoutPreviewResponse>> checkoutPreview(
            @Valid @RequestBody CheckoutPreviewRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireCustomerRole(actor);
        CheckoutPreviewResponse preview = checkoutPreviewService.calculatePreview(request, actor.getUserId());
        return ResponseEntity.ok(new BaseResponse<>(1, preview, "Checkout preview thành công"));
    }

    @PostMapping
    public ResponseEntity<BaseResponse<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireCustomerRole(actor);
        OrderResponse response = orderService.createOrder(request, actor.getUserId(), getPrimaryRole(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Tạo đơn hàng thành công"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<OrderResponse>> getOrderById(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        OrderResponse response = orderService.getOrderById(id, actor.getUserId(), getPrimaryRole(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy thông tin đơn hàng thành công"));
    }

    @GetMapping("/my-orders")
    public ResponseEntity<BaseResponse<PageResponse<OrderResponse>>> getMyOrders(
            @AuthenticationPrincipal AuthenticatedActor actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        requireCustomerRole(actor);
        Pageable pageable = boundedPageable(page, size);
        Page<OrderResponse> response = orderService.getOrdersByUser(actor.getUserId(), actor.getUserId(), getPrimaryRole(actor), pageable);
        return ResponseEntity.ok(new BaseResponse<>(1, PageResponse.from(response), "Lấy danh sách đơn hàng của tôi thành công"));
    }

    @GetMapping("/my-restaurant-orders")
    public ResponseEntity<BaseResponse<PageResponse<OrderResponse>>> getMyRestaurantOrders(
            @AuthenticationPrincipal AuthenticatedActor actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        requireRestaurantOwnerRole(actor);
        Pageable pageable = boundedPageable(page, size);
        Page<OrderResponse> response = orderService.getOrdersByRestaurantOwner(actor.getUserId(), actor.getUserId(), getPrimaryRole(actor), pageable);
        return ResponseEntity.ok(new BaseResponse<>(1, PageResponse.from(response), "Lấy danh sách đơn hàng của nhà hàng tôi sở hữu thành công"));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<BaseResponse<PageResponse<OrderResponse>>> getOrdersByStatus(
            @PathVariable String status,
            @AuthenticationPrincipal AuthenticatedActor actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        requireActor(actor);
        Pageable pageable = boundedPageable(page, size);
        Page<OrderResponse> response = orderService.getOrdersByStatus(status, actor.getUserId(), getPrimaryRole(actor), pageable);
        return ResponseEntity.ok(new BaseResponse<>(1, PageResponse.from(response), "Lấy danh sách đơn hàng theo trạng thái thành công"));
    }

    @GetMapping("/all")
    public ResponseEntity<BaseResponse<PageResponse<OrderResponse>>> getAllOrders(
            @AuthenticationPrincipal AuthenticatedActor actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        requireAdminRole(actor);
        Pageable pageable = boundedPageable(page, size);
        Page<OrderResponse> response = orderService.getAllOrders(actor.getUserId(), getPrimaryRole(actor), pageable);
        return ResponseEntity.ok(new BaseResponse<>(1, PageResponse.from(response), "Lấy tất cả đơn hàng thành công"));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<BaseResponse<OrderResponse>> cancelOrder(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedActor actor,
            @Valid @RequestBody(required = false) CancelOrderRequest cancelRequest) {
        requireActor(actor);
        String reason = cancelRequest != null ? cancelRequest.getReason() : null;
        OrderResponse response = orderService.cancelOrder(id, actor.getUserId(), getPrimaryRole(actor), reason);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Hủy đơn hàng thành công"));
    }

    private Pageable boundedPageable(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ValidationException("Pagination requires page >= 0 and size between 1 and 100");
        }
        return PageRequest.of(page, size);
    }

    private void requireActor(AuthenticatedActor actor) {
        if (actor == null || actor.getUserId() == null) {
            throw new com.delivery.order_service.exception.AccessDeniedException("Yêu cầu đăng nhập");
        }
    }

    private void requireCustomerRole(AuthenticatedActor actor) {
        requireActor(actor);
        if (!actor.isUser()) {
            throw new com.delivery.order_service.exception.AccessDeniedException(
                    "Chỉ khách hàng được tạo hoặc preview đơn hàng");
        }
    }

    private void requireRestaurantOwnerRole(AuthenticatedActor actor) {
        requireActor(actor);
        if (!actor.isShopOwner()) {
            throw new com.delivery.order_service.exception.AccessDeniedException(
                    "Chỉ chủ nhà hàng được xem đơn hàng của nhà hàng mình");
        }
    }

    private void requireAdminRole(AuthenticatedActor actor) {
        requireActor(actor);
        if (!actor.isAdmin()) {
            throw new com.delivery.order_service.exception.AccessDeniedException("Yêu cầu quyền ADMIN");
        }
    }

    private String getPrimaryRole(AuthenticatedActor actor) {
        if (actor == null) return null;
        if (actor.isAdmin()) return RoleConstants.ADMIN;
        if (actor.isShopOwner()) return RoleConstants.RESTAURANT_OWNER;
        if (actor.isShipper()) return RoleConstants.SHIPPER;
        return RoleConstants.USER;
    }
}
