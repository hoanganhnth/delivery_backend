package com.delivery.restaurant_service.repository;
// Package retained during migration to preserve JPA scanning and existing callers.

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
import com.delivery.restaurant.domain.catalog.RestaurantStatus;
@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    @Query("select m from MenuItem m join m.restaurant r where r.ownerPrincipalId = :principalId "
            + "or (:legacyAllowed = true and r.ownerPrincipalId is null and r.creatorId = :legacyId)")
    Page<MenuItem> findManagedByOwner(@Param("principalId") Long principalId,
            @Param("legacyId") Long legacyId, @Param("legacyAllowed") boolean legacyAllowed,
            Pageable pageable);

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
    List<MenuItem> findByRestaurantIdAndStatusAndRestaurantLifecycleStatusNot(
            Long restaurantId, MenuItem.Status status, RestaurantStatus lifecycleStatus, Pageable pageable);
    Page<MenuItem> findPageByRestaurantIdAndStatusAndRestaurantLifecycleStatusNot(
            Long restaurantId, MenuItem.Status status, RestaurantStatus lifecycleStatus, Pageable pageable);
    
    /**
     * Tìm tất cả các món ăn thuộc các nhà hàng được tạo bởi creator cụ thể.
     */
    List<MenuItem> findByRestaurantCreatorId(Long creatorId, Pageable pageable);
    Page<MenuItem> findPageByRestaurantCreatorId(Long creatorId, Pageable pageable);
}
