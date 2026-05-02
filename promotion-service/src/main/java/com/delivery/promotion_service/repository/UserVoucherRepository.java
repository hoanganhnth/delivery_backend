package com.delivery.promotion_service.repository;

import com.delivery.promotion_service.entity.UserVoucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserVoucherRepository extends JpaRepository<UserVoucher, Long> {
    List<UserVoucher> findByUserIdAndStatus(Long userId, UserVoucher.Status status);
    
    Optional<UserVoucher> findByUserIdAndVoucherId(Long userId, Long voucherId);
    
    // For Saga rollback/commit
    List<UserVoucher> findByOrderId(Long orderId);
}
