package com.delivery.livestream_service.repository;

import com.delivery.livestream_service.entity.LivestreamProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LivestreamProductRepository extends JpaRepository<LivestreamProduct, Long> {
    
    List<LivestreamProduct> findByLivestreamId(UUID livestreamId);
    
    Optional<LivestreamProduct> findByLivestreamIdAndProductId(UUID livestreamId, Long productId);
    
    List<LivestreamProduct> findByLivestreamIdAndIsPinned(UUID livestreamId, Boolean isPinned);
}
