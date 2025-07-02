package com.delivery.restaurant_service.repository;

import com.delivery.restaurant_service.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    /**
     * Tìm tất cả các món ăn thuộc một nhà hàng cụ thể.
     */
    List<MenuItem> findByRestaurantId(Long restaurantId);

    /**
     * Tìm các món ăn có tên chứa từ khoá (không phân biệt hoa thường).
     */
    List<MenuItem> findByNameContainingIgnoreCase(String keyword);

    /**
     * Tìm các món ăn theo trạng thái.
     */
    List<MenuItem> findByStatus(MenuItem.Status status);

    /**
     * Kiểm tra xem một món ăn có tồn tại với ID và restaurantId hay không.
     */
    boolean existsByIdAndRestaurantId(Long id, Long restaurantId);

    /**
     * Tìm các món ăn có trạng thái và thuộc một nhà hàng cụ thể.
     */
    List<MenuItem> findByRestaurantIdAndStatus(Long restaurantId, MenuItem.Status status);
}
