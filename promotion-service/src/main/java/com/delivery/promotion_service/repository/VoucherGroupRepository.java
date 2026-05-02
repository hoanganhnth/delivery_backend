package com.delivery.promotion_service.repository;

import com.delivery.promotion_service.entity.VoucherGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VoucherGroupRepository extends JpaRepository<VoucherGroup, Long> {
    Optional<VoucherGroup> findByName(String name);
}
