package com.delivery.promotion_service.repository;

import com.delivery.promotion_service.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, Long> {
    Optional<Voucher> findByCode(String code);

    List<Voucher> findByCreatorTypeAndCreatorId(Voucher.CreatorType creatorType, Long creatorId,
                                                Pageable pageable);
}
