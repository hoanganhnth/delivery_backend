package com.delivery.order_service.repository;

import com.delivery.order_service.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    /**
     * Tìm đơn hàng theo user ID
     */
    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    
    /**
     * Tìm đơn hàng theo restaurant ID
     */
    Page<Order> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId, Pageable pageable);
    
    /**
     * Tìm đơn hàng theo shipper ID
     */
    Page<Order> findByShipperIdOrderByCreatedAtDesc(Long shipperId, Pageable pageable);
    
    /**
     * Tìm đơn hàng theo status
     */
    Page<Order> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
    
    /**
     * Tìm đơn hàng theo user và status
     */
    Page<Order> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status, Pageable pageable);
    
    /**
     * Tìm đơn hàng theo restaurant và status
     */
    Page<Order> findByRestaurantIdAndStatusOrderByCreatedAtDesc(Long restaurantId, String status, Pageable pageable);
    
    /**
     * Lấy tất cả đơn hàng
     */
    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);
    
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
    Page<Order> findByCreatorIdOrderByCreatedAtDesc(Long creatorId, Pageable pageable);

    /**
     * ✅ Admin: Lấy tất cả order chưa hoàn thành (không phải DELIVERED hoặc CANCELLED)
     * Dùng cho tác vụ dọn dẹp/cleanup dữ liệu
     */
    @Query("SELECT o FROM Order o WHERE o.status NOT IN ('DELIVERED', 'CANCELLED') ORDER BY o.createdAt ASC")
    List<Order> findAllNonTerminalOrders();

    // ==================== DASHBOARD STATISTICS ====================

    /**
     * Tổng doanh thu (tất cả đơn DELIVERED)
     */
    @Query("SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o WHERE o.status = 'DELIVERED'")
    java.math.BigDecimal sumTotalRevenueDelivered();

    /**
     * Tổng doanh thu cho 1 nhà hàng (DELIVERED)
     */
    @Query("SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o WHERE o.restaurantId = :restaurantId AND o.status = 'DELIVERED'")
    java.math.BigDecimal sumRevenueByRestaurant(@Param("restaurantId") Long restaurantId);

    /**
     * Đếm đơn theo trạng thái cho 1 nhà hàng
     */
    long countByRestaurantIdAndStatus(Long restaurantId, String status);

    /**
     * Đếm tổng đơn cho 1 nhà hàng
     */
    long countByRestaurantId(Long restaurantId);

    /**
     * Thống kê doanh thu theo tháng trong năm (platform)
     */
    @Query("SELECT MONTH(o.createdAt) as m, COUNT(o) as cnt, COALESCE(SUM(o.totalPrice), 0) as rev " +
           "FROM Order o WHERE o.status = 'DELIVERED' AND YEAR(o.createdAt) = :year " +
           "GROUP BY MONTH(o.createdAt) ORDER BY MONTH(o.createdAt)")
    List<Object[]> monthlyStatsByYear(@Param("year") int year);

    /**
     * Thống kê doanh thu theo tháng trong năm cho 1 nhà hàng
     */
    @Query("SELECT MONTH(o.createdAt) as m, COUNT(o) as cnt, COALESCE(SUM(o.totalPrice), 0) as rev " +
           "FROM Order o WHERE o.restaurantId = :restaurantId AND o.status = 'DELIVERED' AND YEAR(o.createdAt) = :year " +
           "GROUP BY MONTH(o.createdAt) ORDER BY MONTH(o.createdAt)")
    List<Object[]> monthlyStatsByRestaurantAndYear(@Param("restaurantId") Long restaurantId, @Param("year") int year);

    /**
     * Thống kê theo năm (platform)
     */
    @Query("SELECT YEAR(o.createdAt) as y, COUNT(o) as cnt, COALESCE(SUM(o.totalPrice), 0) as rev " +
           "FROM Order o WHERE o.status = 'DELIVERED' " +
           "GROUP BY YEAR(o.createdAt) ORDER BY YEAR(o.createdAt)")
    List<Object[]> yearlyStats();

    /**
     * Thống kê theo năm cho 1 nhà hàng
     */
    @Query("SELECT YEAR(o.createdAt) as y, COUNT(o) as cnt, COALESCE(SUM(o.totalPrice), 0) as rev " +
           "FROM Order o WHERE o.restaurantId = :restaurantId AND o.status = 'DELIVERED' " +
           "GROUP BY YEAR(o.createdAt) ORDER BY YEAR(o.createdAt)")
    List<Object[]> yearlyStatsByRestaurant(@Param("restaurantId") Long restaurantId);

    /**
     * Top nhà hàng theo doanh thu
     */
    @Query("SELECT o.restaurantId, o.restaurantName, COUNT(o), COALESCE(SUM(o.totalPrice), 0) " +
           "FROM Order o WHERE o.status = 'DELIVERED' " +
           "GROUP BY o.restaurantId, o.restaurantName ORDER BY SUM(o.totalPrice) DESC")
    List<Object[]> topRestaurantsByRevenue(Pageable pageable);

    /**
     * Top món ăn cho 1 nhà hàng
     */
    @Query("SELECT oi.menuItemName, SUM(oi.quantity), COALESCE(SUM(oi.price * oi.quantity), 0) " +
           "FROM OrderItem oi JOIN oi.order o " +
           "WHERE o.restaurantId = :restaurantId AND o.status = 'DELIVERED' " +
           "GROUP BY oi.menuItemName ORDER BY SUM(oi.quantity) DESC")
    List<Object[]> topMenuItemsByRestaurant(@Param("restaurantId") Long restaurantId, Pageable pageable);

    /**
     * Đếm trạng thái đơn (toàn bộ)
     */
    @Query("SELECT o.status, COUNT(o) FROM Order o GROUP BY o.status")
    List<Object[]> countAllByStatus();

    /**
     * Đếm trạng thái đơn cho 1 nhà hàng
     */
    @Query("SELECT o.status, COUNT(o) FROM Order o WHERE o.restaurantId = :restaurantId GROUP BY o.status")
    List<Object[]> countByRestaurantIdGroupByStatus(@Param("restaurantId") Long restaurantId);
}
