package com.delivery.livestream_service.repository;

import com.delivery.livestream_service.entity.LivestreamProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LivestreamProductRepository extends JpaRepository<LivestreamProduct, Long> {
    
    @Query("select p from LivestreamProduct p where p.livestreamId = :livestreamId and p.deletedAt is null")
    List<LivestreamProduct> findByLivestreamId(@Param("livestreamId") UUID livestreamId, Pageable pageable);
    
    @Query("select p from LivestreamProduct p where p.livestreamId = :livestreamId and p.productId = :productId and p.deletedAt is null")
    Optional<LivestreamProduct> findByLivestreamIdAndProductId(@Param("livestreamId") UUID livestreamId,
                                                                @Param("productId") Long productId);

    @Query("select p from LivestreamProduct p where p.livestreamId = :livestreamId and p.productId = :productId")
    Optional<LivestreamProduct> findByLivestreamIdAndProductIdIncludingDeleted(@Param("livestreamId") UUID livestreamId,
                                                                                 @Param("productId") Long productId);
    
    @Query("select p from LivestreamProduct p where p.livestreamId = :livestreamId and p.isPinned = :isPinned and p.deletedAt is null")
    List<LivestreamProduct> findByLivestreamIdAndIsPinned(@Param("livestreamId") UUID livestreamId,
                                                           @Param("isPinned") Boolean isPinned, Pageable pageable);

    @Query("select p from LivestreamProduct p where p.livestreamId = :livestreamId and p.isPinned = true and p.deletedAt is null and p.productId in :productIds")
    List<LivestreamProduct> findByLivestreamIdAndIsPinnedTrueAndProductIdIn(@Param("livestreamId") UUID livestreamId,
                                                                              @Param("productIds") List<Long> productIds);
}
