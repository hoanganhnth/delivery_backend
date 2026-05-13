package com.delivery.order_service.service;

import com.delivery.order_service.dto.event.ShipperNotFoundEvent;
import com.delivery.order_service.dto.request.CreateOrderRequest;
import com.delivery.order_service.dto.request.UpdateOrderRequest;
import com.delivery.order_service.dto.response.OrderResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface OrderService {
    
    /**
     * Tạo đơn hàng mới
     */
    OrderResponse createOrder(CreateOrderRequest request, Long userId, String role);
    
    /**
     * Cập nhật đơn hàng
     */
    OrderResponse updateOrder(Long id, UpdateOrderRequest request, Long userId, String role);
    
    /**
     * Lấy thông tin đơn hàng theo ID
     */
    OrderResponse getOrderById(Long id, Long userId, String role);
    
    /**
     * Xóa đơn hàng (chỉ khi status = PENDING)
     */
    void deleteOrder(Long id, Long userId, String role);
    
    /**
     * Lấy danh sách đơn hàng của user
     */
    Page<OrderResponse> getOrdersByUser(Long userId, String role, Pageable pageable);
    
    /**
     * Lấy danh sách đơn hàng của restaurant
     */
    Page<OrderResponse> getOrdersByRestaurant(Long restaurantId, Long userId, String role, Pageable pageable);
    
    /**
     * Lấy danh sách đơn hàng theo restaurant owner (creator ID)
     */
    Page<OrderResponse> getOrdersByRestaurantOwner(Long ownerId, Long userId, String role, Pageable pageable);
    
    /**
     * Lấy danh sách đơn hàng của shipper
     */
    Page<OrderResponse> getOrdersByShipper(Long shipperId, Long userId, String role, Pageable pageable);
    
    /**
     * Lấy đơn hàng theo trạng thái
     */
    Page<OrderResponse> getOrdersByStatus(String status, Long userId, String role, Pageable pageable);
    
    /**
     * Lấy tất cả đơn hàng (Admin only)
     */
    Page<OrderResponse> getAllOrders(Long userId, String role, Pageable pageable);
    
    /**
     * Cập nhật trạng thái đơn hàng
     */
    OrderResponse updateOrderStatus(Long id, String status, Long userId, String role);
    
    /**
     * Phân công shipper cho đơn hàng
     */
    OrderResponse assignShipper(Long orderId, Long shipperId, Long userId, String role);
    
    /**
     * Hủy đơn hàng
     */
    OrderResponse cancelOrder(Long orderId, Long userId, String role, String reason);
    
    /**
     * ✅ Cập nhật order status khi không tìm được shipper
     */
    void updateOrderStatusFromShipperNotFoundEvent(ShipperNotFoundEvent event);

    /**
     * ✅ ADMIN: Huỷ tất cả order chưa hoàn thành (không phải DELIVERED/CANCELLED)
     * Dùng để cleanup dữ liệu cũ bị lỗi. Gọi từ Postman.
     */
    java.util.Map<String, Object> adminCancelAllNonTerminalOrders();
}
