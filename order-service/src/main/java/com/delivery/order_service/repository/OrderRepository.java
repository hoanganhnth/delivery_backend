package com.delivery.order_service.repository;

import com.delivery.order_service.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    /**
     * Tìm đơn hàng theo user ID
     */
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    /**
     * Tìm đơn hàng theo restaurant ID
     */
    List<Order> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);
    
    /**
     * Tìm đơn hàng theo shipper ID
     */
    List<Order> findByShipperIdOrderByCreatedAtDesc(Long shipperId);
    
    /**
     * Tìm đơn hàng theo status
     */
    List<Order> findByStatusOrderByCreatedAtDesc(String status);
    
    /**
     * Tìm đơn hàng theo user và status
     */
    List<Order> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);
    
    /**
     * Tìm đơn hàng theo restaurant và status
     */
    List<Order> findByRestaurantIdAndStatusOrderByCreatedAtDesc(Long restaurantId, String status);
    
    /**
     * Lấy tất cả đơn hàng
     */
    List<Order> findAllByOrderByCreatedAtDesc();
    
    /**
     * Tìm đơn hàng trong khoảng thời gian
     */
    @Query("SELECT o FROM Order o WHERE o.createdAt BETWEEN :startDate AND :endDate ORDER BY o.createdAt DESC")
    List<Order> findOrdersByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    /**
     * Thống kê đơn hàng theo trạng thái
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status")
    long countByStatus(@Param("status") String status);
    
    /**
     * Tìm đơn hàng đang chờ shipper (READY status)
     */
    @Query("SELECT o FROM Order o WHERE o.status = 'READY' ORDER BY o.createdAt ASC")
    List<Order> findOrdersWaitingForShipper();
    
    /**
     * Tìm đơn hàng theo danh sách restaurant IDs (cho restaurant owner)
     */
    @Query("SELECT o FROM Order o WHERE o.restaurantId IN :restaurantIds ORDER BY o.createdAt DESC")
    List<Order> findByRestaurantIdIn(@Param("restaurantIds") List<Long> restaurantIds);
    
    /**
     * ✅ Tìm đơn hàng theo creator ID (chủ nhà hàng)
     * Query trực tiếp từ bảng orders mà không cần JOIN với restaurant
     */
    List<Order> findByCreatorIdOrderByCreatedAtDesc(Long creatorId);
}
