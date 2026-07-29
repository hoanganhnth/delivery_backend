package com.delivery.promotion_service.repository;

import com.delivery.promotion_service.entity.UserVoucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserVoucherRepository extends JpaRepository<UserVoucher, Long> {
    List<UserVoucher> findByUserIdAndStatus(Long userId, UserVoucher.Status status, Pageable pageable);
    
    Optional<UserVoucher> findByUserIdAndVoucherId(Long userId, Long voucherId);
    
}
