package com.delivery.restaurant_service.repository;

import com.delivery.restaurant_service.entity.Restaurant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    /**
     * Tìm tất cả nhà hàng có tên chứa từ khoá (không phân biệt hoa thường).
     */
    List<Restaurant> findByNameContainingIgnoreCase(String keyword);

    /**
     * Tìm danh sách nhà hàng được tạo bởi một người dùng cụ thể (theo creatorId).
     */
    List<Restaurant> findByCreatorId(Long creatorId);

    /**
     * Tìm danh sách nhà hàng được tạo bởi một người dùng (kèm phân trang).
     */
    Page<Restaurant> findByCreatorId(Long creatorId, Pageable pageable);

    /**
     * Tìm nhà hàng theo số điện thoại.
     */
    Optional<Restaurant> findByPhone(String phone);

    /**
     * Tìm nhà hàng theo tên và địa chỉ (cả hai đều chứa từ khoá, không phân biệt hoa thường).
     */
    List<Restaurant> findByNameContainingIgnoreCaseAndAddressContainingIgnoreCase(String name, String address);

    /**
     * Kiểm tra xem một nhà hàng có tồn tại với ID và creatorId hay không.
     * Dùng để xác thực quyền sở hữu.
     */
    boolean existsByIdAndCreatorId(Long id, Long creatorId);

    /**
     * Tìm danh sách nhà hàng đang mở cửa tại thời điểm hiện tại.
     */
    @Query("SELECT r FROM Restaurant r WHERE r.openingHour <= :now AND r.closingHour >= :now")
    List<Restaurant> findRestaurantsOpenAt(LocalTime now);

}
