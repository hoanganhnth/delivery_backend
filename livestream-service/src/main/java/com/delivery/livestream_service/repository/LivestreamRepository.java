package com.delivery.livestream_service.repository;

import com.delivery.livestream_service.entity.Livestream;
import com.delivery.livestream_service.enums.LivestreamStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LivestreamRepository extends JpaRepository<Livestream, UUID> {

    // Sorted by createdAt DESC (newest first)
    List<Livestream> findByStatusOrderByCreatedAtDesc(LivestreamStatus status, Pageable pageable);

    List<Livestream> findBySellerIdOrderByCreatedAtDesc(Long sellerId, Pageable pageable);

    List<Livestream> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId, Pageable pageable);
}
