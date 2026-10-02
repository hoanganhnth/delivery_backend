package com.delivery.restaurant_service.repository;
// Package retained during migration to preserve JPA scanning and existing callers.

import com.delivery.restaurant_service.entity.Restaurant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import com.delivery.restaurant.domain.catalog.RestaurantStatus;

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from Restaurant r where r.id = :id")
    java.util.Optional<Restaurant> findByIdForUpdate(
            @org.springframework.data.repository.query.Param("id") Long id);

    /**
     * Tìm tất cả nhà hàng có tên chứa từ khoá (không phân biệt hoa thường).
     */
    List<Restaurant> findByNameContainingIgnoreCase(String keyword, Pageable pageable);

    Page<Restaurant> findPageByNameContainingIgnoreCase(String keyword, Pageable pageable);

    List<Restaurant> findByNameContainingIgnoreCaseAndLifecycleStatusNot(
            String keyword, RestaurantStatus lifecycleStatus, Pageable pageable);

    Page<Restaurant> findPageByNameContainingIgnoreCaseAndLifecycleStatusNot(
            String keyword, RestaurantStatus lifecycleStatus, Pageable pageable);

    Page<Restaurant> findByLifecycleStatusNot(RestaurantStatus lifecycleStatus, Pageable pageable);

    Page<Restaurant> findByOwnerPrincipalId(Long ownerPrincipalId, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("select r from Restaurant r where r.ownerPrincipalId = :principalId "
            + "or (r.ownerPrincipalId is null and r.creatorId = :legacyCreatorId)")
    Page<Restaurant> findByOwnerPrincipalOrUnmigratedCreator(
            @org.springframework.data.repository.query.Param("principalId") Long principalId,
            @org.springframework.data.repository.query.Param("legacyCreatorId") Long legacyCreatorId,
            Pageable pageable);

    /**
     * Kiểm tra xem một nhà hàng có tồn tại với ID và creatorId hay không.
     * Dùng để xác thực quyền sở hữu.
     */
    boolean existsByIdAndCreatorId(Long id, Long creatorId);

    boolean existsByIdAndOwnerPrincipalId(Long id, Long ownerPrincipalId);

    @org.springframework.data.jpa.repository.Query("select count(r) > 0 from Restaurant r where r.id = :restaurantId and "
            + "(r.ownerPrincipalId = :principalId or "
            + "(r.ownerPrincipalId is null and r.creatorId = :legacyCreatorId))")
    boolean existsByIdAndOwnerPrincipalOrUnmigratedCreator(
            @org.springframework.data.repository.query.Param("restaurantId") Long restaurantId,
            @org.springframework.data.repository.query.Param("principalId") Long principalId,
            @org.springframework.data.repository.query.Param("legacyCreatorId") Long legacyCreatorId);

}
