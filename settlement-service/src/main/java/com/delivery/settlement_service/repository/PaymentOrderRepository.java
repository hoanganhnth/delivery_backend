package com.delivery.settlement_service.repository;

import com.delivery.settlement_service.entity.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    Optional<PaymentOrder> findByPaymentRef(String paymentRef);
}
