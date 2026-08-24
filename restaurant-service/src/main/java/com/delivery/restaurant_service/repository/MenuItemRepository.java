package com.delivery.restaurant_service.repository;

import com.delivery.restaurant_service.entity.MenuItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from MenuItem item where item.id = :id")
    Optional<MenuItem> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from MenuItem item where item.id in :ids order by item.id")
    List<MenuItem> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    /**
     * Tìm tất cả các món ăn thuộc một nhà hàng cụ thể.
     */
    List<MenuItem> findByRestaurantId(Long restaurantId, Pageable pageable);
    Page<MenuItem> findPageByRestaurantId(Long restaurantId, Pageable pageable);

    /**
     * Tìm các món ăn có trạng thái và thuộc một nhà hàng cụ thể.
     */
    List<MenuItem> findByRestaurantIdAndStatus(Long restaurantId, MenuItem.Status status, Pageable pageable);
    Page<MenuItem> findPageByRestaurantIdAndStatus(Long restaurantId, MenuItem.Status status, Pageable pageable);
    
    /**
     * Tìm tất cả các món ăn thuộc các nhà hàng được tạo bởi creator cụ thể.
     */
    List<MenuItem> findByRestaurantCreatorId(Long creatorId, Pageable pageable);
    Page<MenuItem> findPageByRestaurantCreatorId(Long creatorId, Pageable pageable);
}
