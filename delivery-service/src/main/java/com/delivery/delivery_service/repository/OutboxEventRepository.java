package com.delivery.delivery_service.repository;

import com.delivery.delivery_service.entity.OutboxEvent;
import com.delivery.delivery_service.entity.OutboxEvent.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Lấy các event chưa gửi, sắp xếp theo thời gian tạo (FIFO)
     */
    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
