package com.delivery.order_service.controller;

import com.delivery.order_service.common.constants.ApiPathConstants;
import com.delivery.order_service.common.constants.HttpHeaderConstants;
import com.delivery.order_service.dto.request.CreateOrderRequest;
import com.delivery.order_service.dto.request.UpdateOrderRequest;
import com.delivery.order_service.dto.response.OrderResponse;
import com.delivery.order_service.payload.BaseResponse;
import com.delivery.order_service.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(ApiPathConstants.ORDERS)
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<BaseResponse<OrderResponse>> createOrder(
            @RequestBody CreateOrderRequest request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        OrderResponse response = orderService.createOrder(request, userId, role);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Tạo đơn hàng thành công"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<OrderResponse>> getOrderById(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        OrderResponse response = orderService.getOrderById(id, userId, role);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy thông tin đơn hàng thành công"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BaseResponse<OrderResponse>> updateOrder(
            @PathVariable Long id,
            @RequestBody UpdateOrderRequest request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        OrderResponse response = orderService.updateOrder(id, request, userId, role);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Cập nhật đơn hàng thành công"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse<Void>> deleteOrder(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        orderService.deleteOrder(id, userId, role);
        return ResponseEntity.ok(new BaseResponse<>(1, null, "Xóa đơn hàng thành công"));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<BaseResponse<List<OrderResponse>>> getOrdersByUser(
            @PathVariable Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long currentUserId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        List<OrderResponse> response = orderService.getOrdersByUser(userId, role);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy danh sách đơn hàng của user thành công"));
    }

    @GetMapping("/my-orders")
    public ResponseEntity<BaseResponse<List<OrderResponse>>> getMyOrders(
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        List<OrderResponse> response = orderService.getOrdersByUser(userId, role);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy danh sách đơn hàng của tôi thành công"));
    }

    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<BaseResponse<List<OrderResponse>>> getOrdersByRestaurant(
            @PathVariable Long restaurantId,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        List<OrderResponse> response = orderService.getOrdersByRestaurant(restaurantId, userId, role);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy danh sách đơn hàng của nhà hàng thành công"));
    }
    
    @GetMapping("/restaurant-owner/{ownerId}")
    public ResponseEntity<BaseResponse<List<OrderResponse>>> getOrdersByRestaurantOwner(
            @PathVariable Long ownerId,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        List<OrderResponse> response = orderService.getOrdersByRestaurantOwner(ownerId, userId, role);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy danh sách đơn hàng của chủ nhà hàng thành công"));
    }
    
    @GetMapping("/my-restaurant-orders")
    public ResponseEntity<BaseResponse<List<OrderResponse>>> getMyRestaurantOrders(
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        List<OrderResponse> response = orderService.getOrdersByRestaurantOwner(userId, userId, role);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy danh sách đơn hàng của nhà hàng tôi sở hữu thành công"));
    }

    @GetMapping("/shipper/{shipperId}")
    public ResponseEntity<BaseResponse<List<OrderResponse>>> getOrdersByShipper(
            @PathVariable Long shipperId,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        List<OrderResponse> response = orderService.getOrdersByShipper(shipperId, userId, role);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy danh sách đơn hàng của shipper thành công"));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<BaseResponse<List<OrderResponse>>> getOrdersByStatus(
            @PathVariable String status,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        List<OrderResponse> response = orderService.getOrdersByStatus(status, userId, role);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy danh sách đơn hàng theo trạng thái thành công"));
    }

    @GetMapping("/all")
    public ResponseEntity<BaseResponse<List<OrderResponse>>> getAllOrders(
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        List<OrderResponse> response = orderService.getAllOrders(userId, role);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy tất cả đơn hàng thành công"));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<BaseResponse<OrderResponse>> updateOrderStatus(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        OrderResponse response = orderService.updateOrderStatus(id, status, userId, role);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Cập nhật trạng thái đơn hàng thành công"));
    }

    @PutMapping("/{orderId}/assign-shipper/{shipperId}")
    public ResponseEntity<BaseResponse<OrderResponse>> assignShipper(
            @PathVariable Long orderId,
            @PathVariable Long shipperId,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        OrderResponse response = orderService.assignShipper(orderId, shipperId, userId, role);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Phân công shipper thành công"));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<BaseResponse<OrderResponse>> cancelOrder(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        OrderResponse response = orderService.cancelOrder(id, userId, role);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Hủy đơn hàng thành công"));
    }
}
