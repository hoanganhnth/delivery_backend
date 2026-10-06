package com.delivery.promotion_service.repository;

import com.delivery.promotion_service.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
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

    Page<Voucher> findByDeletedAtIsNull(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select voucher from Voucher voucher where voucher.id = :voucherId")
    Optional<Voucher> findByIdForUpdate(@Param("voucherId") Long voucherId);

    @Query("select voucher from Voucher voucher where voucher.deletedAt is null "
            + "and voucher.creatorType = :creatorType and voucher.creatorId = :creatorId")
    List<Voucher> findByCreatorTypeAndCreatorId(@Param("creatorType") Voucher.CreatorType creatorType,
                                                @Param("creatorId") Long creatorId,
                                                Pageable pageable);

    @Query("select voucher from Voucher voucher where voucher.deletedAt is null "
            + "and voucher.creatorType = :creatorType and voucher.ownerPrincipalId = :ownerPrincipalId")
    List<Voucher> findByCreatorTypeAndOwnerPrincipalId(@Param("creatorType") Voucher.CreatorType creatorType,
                                                       @Param("ownerPrincipalId") Long ownerPrincipalId,
                                                       Pageable pageable);

    @Query("select voucher from Voucher voucher where voucher.deletedAt is null "
            + "and voucher.creatorType = :creatorType and "
            + "(voucher.ownerPrincipalId = :principalId or "
            + "(voucher.ownerPrincipalId is null and voucher.creatorId = :legacyId))")
    List<Voucher> findByOwnerPrincipalOrLegacy(@Param("creatorType") Voucher.CreatorType creatorType,
                                               @Param("principalId") Long principalId,
                                               @Param("legacyId") Long legacyId,
                                               Pageable pageable);

    @Query("select voucher from Voucher voucher where voucher.deletedAt is null "
            + "and voucher.approvalStatus = :approvalStatus order by voucher.createdAt asc")
    List<Voucher> findByApprovalStatusOrderByCreatedAtAsc(@Param("approvalStatus") String approvalStatus,
                                                        Pageable pageable);
}
