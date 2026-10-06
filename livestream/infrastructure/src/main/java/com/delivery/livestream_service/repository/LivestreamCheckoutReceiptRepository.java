package com.delivery.livestream_service.repository;

import com.delivery.livestream_service.entity.LivestreamCheckoutReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LivestreamCheckoutReceiptRepository extends JpaRepository<LivestreamCheckoutReceipt, Long> {
    Optional<LivestreamCheckoutReceipt> findByActorPrincipalIdAndIdempotencyKey(Long actorPrincipalId,
                                                                                  String idempotencyKey);
}
