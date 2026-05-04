package com.delivery.promotion_service.repository;

import com.delivery.promotion_service.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, Long> {
    Optional<Voucher> findByCode(String code);

    // Get active vouchers for a specific shop
    @Query("SELECT v FROM Voucher v WHERE v.active = true " +
            "AND v.endTime > :currentTime " +
            "AND (v.scopeType = 'ALL' OR (v.scopeType = 'SHOP' AND v.scopeRefId = :shopId))")
    List<Voucher> findAvailableForShop(Long shopId, LocalDateTime currentTime);

    List<Voucher> findByCreatorTypeAndCreatorId(Voucher.CreatorType creatorType, Long creatorId);
}
