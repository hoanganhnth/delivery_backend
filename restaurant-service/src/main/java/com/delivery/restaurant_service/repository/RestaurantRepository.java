package com.delivery.restaurant_service.repository;

import com.delivery.restaurant_service.entity.Restaurant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    /**
     * Tìm tất cả nhà hàng có tên chứa từ khoá (không phân biệt hoa thường).
     */
    List<Restaurant> findByNameContainingIgnoreCase(String keyword, Pageable pageable);

    /**
     * Tìm danh sách nhà hàng được tạo bởi một người dùng cụ thể (theo creatorId).
     */
    Page<Restaurant> findByCreatorId(Long creatorId, Pageable pageable);

    /**
     * Kiểm tra xem một nhà hàng có tồn tại với ID và creatorId hay không.
     * Dùng để xác thực quyền sở hữu.
     */
    boolean existsByIdAndCreatorId(Long id, Long creatorId);

}
