package com.delivery.livestream_service.repository;

import com.delivery.livestream_service.entity.LivestreamEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LivestreamEventRepository extends JpaRepository<LivestreamEvent, Long> {
    
    List<LivestreamEvent> findByLivestreamId(UUID livestreamId);
}
