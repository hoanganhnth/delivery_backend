package com.delivery.promotion_service.repository;

import com.delivery.promotion_service.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, Long> {
    Optional<Voucher> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select voucher from Voucher voucher where voucher.id = :voucherId")
    Optional<Voucher> findByIdForUpdate(@Param("voucherId") Long voucherId);

    List<Voucher> findByCreatorTypeAndCreatorId(Voucher.CreatorType creatorType, Long creatorId,
                                                Pageable pageable);

    List<Voucher> findByCreatorTypeAndOwnerPrincipalId(Voucher.CreatorType creatorType, Long ownerPrincipalId,
                                                       Pageable pageable);

    @Query("select voucher from Voucher voucher where voucher.creatorType = :creatorType and "
            + "(voucher.ownerPrincipalId = :principalId or "
            + "(voucher.ownerPrincipalId is null and voucher.creatorId = :legacyId))")
    List<Voucher> findByOwnerPrincipalOrLegacy(@Param("creatorType") Voucher.CreatorType creatorType,
                                               @Param("principalId") Long principalId,
                                               @Param("legacyId") Long legacyId,
                                               Pageable pageable);

    List<Voucher> findByApprovalStatusOrderByCreatedAtAsc(String approvalStatus, Pageable pageable);
}
