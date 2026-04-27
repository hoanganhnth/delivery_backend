package com.delivery.delivery_service.repository;

import com.delivery.delivery_service.entity.Delivery;
import com.delivery.delivery_service.entity.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    /**
     * Tìm delivery theo order ID
     */
    Optional<Delivery> findByOrderId(Long orderId);

    /**
     * Tìm các delivery của shipper
     */
    List<Delivery> findByShipperIdOrderByCreatedAtDesc(Long shipperId);

    /**
     * Tìm các delivery theo trạng thái
     */
    List<Delivery> findByStatusOrderByCreatedAtDesc(DeliveryStatus status);

    /**
     * Tìm các delivery của shipper theo trạng thái
     */
    List<Delivery> findByShipperIdAndStatusOrderByCreatedAtDesc(Long shipperId, DeliveryStatus status);

    /**
     * Kiểm tra order đã có delivery chưa
     */
    boolean existsByOrderId(Long orderId);

    /**
     * Lấy delivery đang active của shipper
     */
    @Query("SELECT d FROM Delivery d WHERE d.shipperId = :shipperId AND d.status IN (com.delivery.delivery_service.entity.DeliveryStatus.ASSIGNED, com.delivery.delivery_service.entity.DeliveryStatus.PICKED_UP, com.delivery.delivery_service.entity.DeliveryStatus.DELIVERING) ORDER BY d.createdAt DESC")
    List<Delivery> findActiveDeliveriesByShipper(@Param("shipperId") Long shipperId);

    /**
     * Đếm số delivery đang active của shipper
     */
    @Query("SELECT COUNT(d) FROM Delivery d WHERE d.shipperId = :shipperId AND d.status IN (com.delivery.delivery_service.entity.DeliveryStatus.ASSIGNED, com.delivery.delivery_service.entity.DeliveryStatus.PICKED_UP, com.delivery.delivery_service.entity.DeliveryStatus.DELIVERING)")
    long countActiveDeliveriesByShipper(@Param("shipperId") Long shipperId);

    /**
     * ✅ Admin: Lấy tất cả delivery chưa hoàn thành (không phải DELIVERED/CANCELLED)
     * Dùng cho tác vụ đồng bộ/cleanup
     */
    @Query("SELECT d FROM Delivery d WHERE d.status NOT IN (com.delivery.delivery_service.entity.DeliveryStatus.DELIVERED, com.delivery.delivery_service.entity.DeliveryStatus.CANCELLED) ORDER BY d.createdAt ASC")
    List<Delivery> findAllNonTerminalDeliveries();
}
