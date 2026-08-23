package com.delivery.delivery_service.service;

import com.delivery.delivery_service.common.constants.KafkaTopicConstants;
import com.delivery.delivery_service.common.constants.RoleConstants;
import com.delivery.delivery_service.entity.Delivery;
import com.delivery.delivery_service.entity.DeliveryBatch;
import com.delivery.delivery_service.entity.DeliveryBatchItem;
import com.delivery.delivery_service.entity.DeliveryBatchItemStatus;
import com.delivery.delivery_service.entity.DeliveryBatchStatus;
import com.delivery.delivery_service.entity.DeliveryStatus;
import com.delivery.delivery_service.exception.InvalidStatusException;
import com.delivery.delivery_service.repository.DeliveryBatchItemRepository;
import com.delivery.delivery_service.repository.DeliveryBatchRepository;
import com.delivery.delivery_service.repository.DeliveryRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Retires expired/rejected offers and emits the durable Match/Settlement release intent. */
@Service
public class DeliveryBatchLifecycleService {
    @Value("${delivery.batch.enabled:false}")
    private boolean batchEnabled;
    private final DeliveryBatchRepository batchRepository;
    private final DeliveryBatchItemRepository itemRepository;
    private final DeliveryRepository deliveryRepository;
    private final OutboxService outboxService;
    private final DeliveryEventPublisher eventPublisher;

    public DeliveryBatchLifecycleService(DeliveryBatchRepository batchRepository,
                                         DeliveryBatchItemRepository itemRepository,
                                         DeliveryRepository deliveryRepository,
                                         OutboxService outboxService,
                                         DeliveryEventPublisher eventPublisher) {
        this.batchRepository = batchRepository;
        this.itemRepository = itemRepository;
        this.deliveryRepository = deliveryRepository;
        this.outboxService = outboxService;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelayString = "${delivery.batch.expiry-scan-ms:1000}")
    @Transactional
    public void expireOffers() {
        if (!batchEnabled) return;
        batchRepository.findExpiredOffersForUpdate(LocalDateTime.now(), PageRequest.of(0, 100))
                .forEach(this::retire);
    }

    @Transactional
    public void retire(DeliveryBatch batch) {
        if (batch == null || batch.getStatus() != DeliveryBatchStatus.OFFERED) return;
        List<DeliveryBatchItem> items = itemRepository.findByBatchIdOrderByPickupSequenceAsc(batch.getBatchId());
        List<Long> deliveryIds = new java.util.ArrayList<>();
        List<String> sessions = new java.util.ArrayList<>();
        for (DeliveryBatchItem item : items) {
            Delivery delivery = deliveryRepository.findByIdForUpdate(item.getDeliveryId())
                    .orElseThrow(() -> new InvalidStatusException("Batch delivery disappeared"));
            deliveryIds.add(delivery.getId());
            sessions.add(delivery.getOfferedMatchingSessionId() == null ? "" : delivery.getOfferedMatchingSessionId());
            if (delivery.getBatchId() != null && delivery.getBatchId().equals(batch.getBatchId())) {
                delivery.setBatchId(null);
                delivery.setBatchSequence(null);
                delivery.setOfferedShipperId(null);
                delivery.setOfferExpiresAt(null);
                delivery.setOfferedMatchingSessionId(null);
                delivery.setStatus(DeliveryStatus.FINDING_SHIPPER);
                delivery.setUpdatedAt(LocalDateTime.now());
                deliveryRepository.save(delivery);
                publishRejected(delivery, batch.getShipperId(), "Batch offer expired or was rejected",
                        batchWaveForNext(batch));
            }
            item.setItemStatus(DeliveryBatchItemStatus.CANCELLED);
            item.setUpdatedAt(LocalDateTime.now());
        }
        itemRepository.saveAll(items);
        batch.setStatus(DeliveryBatchStatus.RETIRED);
        batch.setUpdatedAt(LocalDateTime.now());
        batchRepository.saveAndFlush(batch);
        publishRelease(batch, deliveryIds, sessions);
    }

    @Transactional
    public void reject(UUID batchId, Long shipperId, String role, String reason) {
        if (!batchEnabled) throw new InvalidStatusException("Delivery batch dispatch is disabled");
        if (!RoleConstants.SHIPPER.equals(role) || shipperId == null || shipperId <= 0 || batchId == null) {
            throw new com.delivery.delivery_service.exception.AccessDeniedException("Chỉ shipper mới có thể từ chối batch");
        }
        if (reason == null || reason.isBlank()) throw new InvalidStatusException("Batch reject reason is required");
        DeliveryBatch batch = batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new InvalidStatusException("Không tìm thấy batch offer"));
        if (!shipperId.equals(batch.getShipperId())) {
            throw new com.delivery.delivery_service.exception.AccessDeniedException("Batch không thuộc shipper này");
        }
        if (batch.getStatus() != DeliveryBatchStatus.OFFERED) return;
        retire(batch);
    }

    /**
     * A post-accept cancellation is atomic at batch scope. Partial cancellation
     * would clear the shipper reservation while the remaining delivery rows are
     * still assigned to the same shipper.
     */
    @Transactional
    public Delivery cancelAcceptedBatch(UUID batchId, Long shipperId, String reason) {
        if (!batchEnabled) throw new InvalidStatusException("Delivery batch dispatch is disabled");
        if (batchId == null || shipperId == null || shipperId <= 0) {
            throw new InvalidStatusException("Batch and shipper are required");
        }
        DeliveryBatch batch = batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new InvalidStatusException("Không tìm thấy batch đang hoạt động"));
        if (!shipperId.equals(batch.getShipperId())) {
            throw new com.delivery.delivery_service.exception.AccessDeniedException("Batch không thuộc shipper này");
        }
        if (batch.getStatus() != DeliveryBatchStatus.ACCEPTED) {
            throw new InvalidStatusException("Chỉ có thể huỷ batch trước khi pickup");
        }

        List<DeliveryBatchItem> items = itemRepository.findByBatchIdOrderByPickupSequenceAsc(batchId);
        List<Delivery> deliveries = items.stream().map(item -> deliveryRepository.findByIdForUpdate(item.getDeliveryId())
                .orElseThrow(() -> new InvalidStatusException("Batch delivery không tồn tại"))).toList();
        if (deliveries.isEmpty() || deliveries.stream().anyMatch(delivery ->
                !DeliveryStatus.ASSIGNED.equals(delivery.getStatus())
                        || !shipperId.equals(delivery.getShipperId()))) {
            throw new InvalidStatusException("Batch chỉ có thể huỷ trước khi pickup toàn bộ item");
        }

        LocalDateTime now = LocalDateTime.now();
        List<Long> deliveryIds = new java.util.ArrayList<>();
        List<String> sessions = new java.util.ArrayList<>();
        for (Delivery delivery : deliveries) {
            deliveryIds.add(delivery.getId());
            sessions.add(delivery.getOfferedMatchingSessionId() == null ? "" : delivery.getOfferedMatchingSessionId());
            delivery.setShipperId(null);
            delivery.setBatchId(null);
            delivery.setBatchSequence(null);
            delivery.setOfferedShipperId(shipperId);
            delivery.setOfferExpiresAt(null);
            delivery.setStatus(DeliveryStatus.FINDING_SHIPPER);
            delivery.setRejectReason(reason == null || reason.isBlank() ? "Batch cancelled by shipper" : reason);
            delivery.setUpdatedAt(now);
            deliveryRepository.save(delivery);
            eventPublisher.publishShipperStatusChange(shipperId, "AVAILABLE", delivery.getId(),
                    delivery.getOrderId(), batchId);
            publishRejected(delivery, shipperId, delivery.getRejectReason(), batchWaveForNext(batch));
        }
        items.forEach(item -> {
            item.setItemStatus(DeliveryBatchItemStatus.CANCELLED);
            item.setUpdatedAt(now);
        });
        itemRepository.saveAll(items);
        batch.setStatus(DeliveryBatchStatus.CANCELLED);
        batch.setUpdatedAt(now);
        batchRepository.saveAndFlush(batch);
        publishRelease(batch, deliveryIds, sessions);
        return deliveries.get(0);
    }

    private void publishRelease(DeliveryBatch batch, List<Long> deliveryIds, List<String> sessions) {
        List<String> holdIds = batch.getCodHoldIds() == null || batch.getCodHoldIds().isBlank()
                ? List.of() : List.of(batch.getCodHoldIds().split(","));
        Map<String, Object> payload = new HashMap<>();
        payload.put("batchId", batch.getBatchId().toString());
        payload.put("holdIds", holdIds);
        payload.put("target", "RELEASED");
        payload.put("deliveryIds", deliveryIds);
        payload.put("matchingSessionIds", sessions);
        UUID eventId = UUID.nameUUIDFromBytes(("batch-hold:RELEASED:" + batch.getBatchId())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        outboxService.saveEvent(eventId, "DELIVERY_BATCH", batch.getBatchId().toString(),
                "BATCH_COD_HOLD_RELEASED", KafkaTopicConstants.BATCH_RELEASED_TOPIC,
                batch.getBatchId().toString(), payload);
    }

    private void publishRejected(Delivery delivery, Long shipperId, String reason, int nextWave) {
        Map<String, Object> event = new HashMap<>();
        event.put("orderId", delivery.getOrderId());
        event.put("deliveryId", delivery.getId());
        event.put("rejectedShipperId", shipperId);
        event.put("rejectReason", reason);
        event.put("pickupAddress", delivery.getPickupAddress());
        event.put("pickupLat", delivery.getPickupLat());
        event.put("pickupLng", delivery.getPickupLng());
        event.put("deliveryAddress", delivery.getDeliveryAddress());
        event.put("deliveryLat", delivery.getDeliveryLat());
        event.put("deliveryLng", delivery.getDeliveryLng());
        event.put("eventType", "SHIPPER_REJECTED");
        event.put("batchWave", nextWave);
        event.put("timestamp", System.currentTimeMillis());
        outboxService.saveEvent("DELIVERY", delivery.getId().toString(), "SHIPPER_REJECTED",
                KafkaTopicConstants.SHIPPER_REJECTED_TOPIC, delivery.getOrderId().toString(), event);
    }

    private int batchWaveForNext(DeliveryBatch batch) {
        return Math.max(1, batch.getWaveNumber() + 1);
    }
}
