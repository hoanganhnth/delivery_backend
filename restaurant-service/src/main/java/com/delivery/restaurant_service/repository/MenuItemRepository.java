package com.delivery.restaurant_service.repository;

import com.delivery.restaurant_service.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;

import java.util.List;
@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    /**
     * Tìm tất cả các món ăn thuộc một nhà hàng cụ thể.
     */
    List<MenuItem> findByRestaurantId(Long restaurantId, Pageable pageable);

    /**
     * Tìm các món ăn có trạng thái và thuộc một nhà hàng cụ thể.
     */
    List<MenuItem> findByRestaurantIdAndStatus(Long restaurantId, MenuItem.Status status, Pageable pageable);
    
    /**
     * Tìm tất cả các món ăn thuộc các nhà hàng được tạo bởi creator cụ thể.
     */
    List<MenuItem> findByRestaurantCreatorId(Long creatorId, Pageable pageable);
}
