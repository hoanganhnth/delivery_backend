package com.delivery.delivery_service.service;

import com.delivery.delivery_service.entity.Delivery;
import com.delivery.delivery_service.entity.DeliveryBatch;
import com.delivery.delivery_service.entity.DeliveryBatchItem;
import com.delivery.delivery_service.entity.DeliveryBatchItemStatus;
import com.delivery.delivery_service.entity.DeliveryBatchStatus;
import com.delivery.delivery_service.entity.DeliveryStatus;
import com.delivery.delivery_service.repository.DeliveryBatchItemRepository;
import com.delivery.delivery_service.repository.DeliveryBatchRepository;
import com.delivery.delivery_service.repository.DeliveryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Keeps the batch aggregate and its item projection in step with delivery progress. */
@Service
public class DeliveryBatchProgressService {

    private final DeliveryBatchRepository batchRepository;
    private final DeliveryBatchItemRepository itemRepository;
    private final DeliveryRepository deliveryRepository;
    private final OutboxService outboxService;

    public DeliveryBatchProgressService(DeliveryBatchRepository batchRepository,
                                        DeliveryBatchItemRepository itemRepository,
                                        DeliveryRepository deliveryRepository,
                                        OutboxService outboxService) {
        this.batchRepository = batchRepository;
        this.itemRepository = itemRepository;
        this.deliveryRepository = deliveryRepository;
        this.outboxService = outboxService;
    }

    @Transactional
    public boolean apply(Delivery delivery, DeliveryStatus deliveryStatus) {
        if (delivery == null || delivery.getBatchId() == null || delivery.getId() == null
                || deliveryStatus == null) return true;

        DeliveryBatch batch = batchRepository.findByIdForUpdate(delivery.getBatchId()).orElse(null);
        if (batch == null || batch.getStatus() == DeliveryBatchStatus.RETIRED
                || batch.getStatus() == DeliveryBatchStatus.CANCELLED) return true;
        DeliveryBatchItem item = itemRepository.findByBatchIdAndDeliveryIdForUpdate(
                delivery.getBatchId(), delivery.getId()).orElse(null);
        if (item == null) return true;

        DeliveryBatchItemStatus nextItemStatus = itemStatusFor(deliveryStatus);
        if (nextItemStatus == null) return false;
        item.setItemStatus(nextItemStatus);
        item.setUpdatedAt(LocalDateTime.now());
        itemRepository.save(item);

        java.util.List<DeliveryBatchItem> items = itemRepository
                .findByBatchIdOrderByPickupSequenceAsc(delivery.getBatchId());
        boolean allTerminal = !items.isEmpty() && items.stream()
                .allMatch(candidate -> candidate.getItemStatus() == DeliveryBatchItemStatus.DELIVERED
                        || candidate.getItemStatus() == DeliveryBatchItemStatus.RETURNED);
        boolean newlyCompleted = allTerminal && batch.getStatus() != DeliveryBatchStatus.COMPLETED;
        if (allTerminal) {
            batch.setStatus(DeliveryBatchStatus.COMPLETED);
            batch.setCompletedAt(LocalDateTime.now());
        } else if (deliveryStatus == DeliveryStatus.DELIVERING
                || deliveryStatus == DeliveryStatus.DELIVERED) {
            batch.setStatus(DeliveryBatchStatus.DELIVERING);
        } else if (deliveryStatus == DeliveryStatus.PICKED_UP) {
            batch.setStatus(DeliveryBatchStatus.PICKED_UP);
        }
        batch.setUpdatedAt(LocalDateTime.now());
        batchRepository.save(batch);
        if (newlyCompleted) publishBatchCompleted(batch, items);
        return allTerminal;
    }

    /** Keeps a batch route reserved while one post-pickup item is returning. */
    @Transactional
    public boolean applyExceptionReturn(Delivery delivery, boolean returned) {
        if (delivery == null || delivery.getBatchId() == null || delivery.getId() == null) return true;
        DeliveryBatch batch = batchRepository.findByIdForUpdate(delivery.getBatchId()).orElse(null);
        if (batch == null || batch.getStatus() == DeliveryBatchStatus.RETIRED
                || batch.getStatus() == DeliveryBatchStatus.CANCELLED) return true;
        DeliveryBatchItem item = itemRepository.findByBatchIdAndDeliveryIdForUpdate(
                delivery.getBatchId(), delivery.getId()).orElse(null);
        if (item == null) return true;
        item.setItemStatus(returned ? DeliveryBatchItemStatus.RETURNED : DeliveryBatchItemStatus.RETURNING);
        item.setUpdatedAt(LocalDateTime.now());
        itemRepository.save(item);

        java.util.List<DeliveryBatchItem> items = itemRepository
                .findByBatchIdOrderByPickupSequenceAsc(delivery.getBatchId());
        boolean allTerminal = !items.isEmpty() && items.stream()
                .allMatch(candidate -> candidate.getItemStatus() == DeliveryBatchItemStatus.DELIVERED
                        || candidate.getItemStatus() == DeliveryBatchItemStatus.RETURNED);
        boolean newlyCompleted = allTerminal && batch.getStatus() != DeliveryBatchStatus.COMPLETED;
        if (allTerminal) {
            batch.setStatus(DeliveryBatchStatus.COMPLETED);
            batch.setCompletedAt(LocalDateTime.now());
        } else if (!returned) {
            // The batch aggregate stays active; its item projection carries the
            // explicit RETURNING fact without introducing a legacy batch status.
            batch.setStatus(DeliveryBatchStatus.DELIVERING);
        }
        batch.setUpdatedAt(LocalDateTime.now());
        batchRepository.save(batch);
        if (newlyCompleted) publishBatchCompleted(batch, items);
        return allTerminal;
    }

    private void publishBatchCompleted(DeliveryBatch batch, java.util.List<DeliveryBatchItem> items) {
        java.util.List<Long> deliveryIds = new java.util.ArrayList<>();
        java.util.List<String> sessions = new java.util.ArrayList<>();
        for (DeliveryBatchItem item : items) {
            deliveryRepository.findById(item.getDeliveryId()).ifPresent(delivery -> {
                deliveryIds.add(delivery.getId());
                sessions.add(delivery.getOfferedMatchingSessionId() == null
                        ? "" : delivery.getOfferedMatchingSessionId());
            });
        }
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("batchId", batch.getBatchId().toString());
        payload.put("deliveryIds", deliveryIds);
        payload.put("matchingSessionIds", sessions);
        java.util.UUID eventId = java.util.UUID.nameUUIDFromBytes(
                ("batch-completed:" + batch.getBatchId()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        outboxService.saveEvent(eventId, "DELIVERY_BATCH", batch.getBatchId().toString(),
                "BATCH_COMPLETED", com.delivery.delivery_service.common.constants.KafkaTopicConstants.BATCH_COMPLETED_TOPIC,
                batch.getBatchId().toString(), payload);
    }

    private DeliveryBatchItemStatus itemStatusFor(DeliveryStatus status) {
        return switch (status) {
            case PICKED_UP -> DeliveryBatchItemStatus.PICKED_UP;
            case DELIVERING -> DeliveryBatchItemStatus.DELIVERING;
            case DELIVERED -> DeliveryBatchItemStatus.DELIVERED;
            case RETURNING -> DeliveryBatchItemStatus.RETURNING;
            case RETURNED -> DeliveryBatchItemStatus.RETURNED;
            case CANCELLED -> DeliveryBatchItemStatus.CANCELLED;
            default -> null;
        };
    }
}
