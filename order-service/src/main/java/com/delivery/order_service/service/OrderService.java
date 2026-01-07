package com.delivery.order_service.service;

import com.delivery.order_service.dto.event.ShipperNotFoundEvent;
import com.delivery.order_service.dto.request.CreateOrderRequest;
import com.delivery.order_service.dto.request.UpdateOrderRequest;
import com.delivery.order_service.dto.response.OrderResponse;

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
    List<OrderResponse> getOrdersByUser(Long userId, String role);
    
    /**
     * Lấy danh sách đơn hàng của restaurant
     */
    List<OrderResponse> getOrdersByRestaurant(Long restaurantId, Long userId, String role);
    
    /**
     * Lấy danh sách đơn hàng theo restaurant owner (creator ID)
     */
    List<OrderResponse> getOrdersByRestaurantOwner(Long ownerId, Long userId, String role);
    
    /**
     * Lấy danh sách đơn hàng của shipper
     */
    List<OrderResponse> getOrdersByShipper(Long shipperId, Long userId, String role);
    
    /**
     * Lấy đơn hàng theo trạng thái
     */
    List<OrderResponse> getOrdersByStatus(String status, Long userId, String role);
    
    /**
     * Lấy tất cả đơn hàng (Admin only)
     */
    List<OrderResponse> getAllOrders(Long userId, String role);
    
    /**
     * Cập nhật trạng thái đơn hàng
     */
    OrderResponse updateOrderStatus(Long id, String status, Long userId, String role);
    
    /**
     * Phân công shipper cho đơn hàng
     */
    OrderResponse assignShipper(Long orderId, Long shipperId, Long userId, String role);
    
    /**
     * Hủy đơn hàng (chỉ khi đơn hàng mới tạo và chưa được gán shipper)
     */
    OrderResponse cancelOrder(Long orderId, Long userId, String role);
    
    /**
     * ✅ Cập nhật order status khi không tìm được shipper
     */
    void updateOrderStatusFromShipperNotFoundEvent(ShipperNotFoundEvent event);
}
