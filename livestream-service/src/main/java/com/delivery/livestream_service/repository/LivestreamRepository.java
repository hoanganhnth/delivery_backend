package com.delivery.livestream_service.repository;

import com.delivery.livestream_service.entity.Livestream;
import com.delivery.livestream_service.enums.LivestreamStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LivestreamRepository extends JpaRepository<Livestream, UUID> {

    // Sorted by createdAt DESC (newest first)
    List<Livestream> findByStatusOrderByCreatedAtDesc(LivestreamStatus status);

    List<Livestream> findBySellerIdOrderByCreatedAtDesc(Long sellerId);

    List<Livestream> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);

    // Keep legacy unsorted variants for internal use if needed
    List<Livestream> findByStatus(LivestreamStatus status);

    List<Livestream> findBySellerId(Long sellerId);

    List<Livestream> findByRestaurantId(Long restaurantId);

    Optional<Livestream> findByRoomId(String roomId);
}
