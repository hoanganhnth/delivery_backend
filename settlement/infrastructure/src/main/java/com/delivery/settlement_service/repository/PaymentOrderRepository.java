package com.delivery.settlement_service.repository;

import com.delivery.settlement_service.entity.PaymentOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    Optional<PaymentOrder> findByPaymentRef(String paymentRef);

    /** Serializes callback/confirmation processing of one payment. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentOrder p where p.paymentRef = :paymentRef")
    Optional<PaymentOrder> findByPaymentRefForUpdate(@Param("paymentRef") String paymentRef);
}
