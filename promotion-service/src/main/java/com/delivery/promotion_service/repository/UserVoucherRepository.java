package com.delivery.promotion_service.repository;

import com.delivery.promotion_service.entity.UserVoucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserVoucherRepository extends JpaRepository<UserVoucher, Long> {
    List<UserVoucher> findByUserIdAndStatus(Long userId, UserVoucher.Status status, Pageable pageable);
    
    Optional<UserVoucher> findByUserIdAndVoucherId(Long userId, Long voucherId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select userVoucher from UserVoucher userVoucher "
            + "where userVoucher.userId = :userId and userVoucher.voucherId = :voucherId")
    Optional<UserVoucher> findByUserIdAndVoucherIdForUpdate(
            @Param("userId") Long userId, @Param("voucherId") Long voucherId);
    
}
