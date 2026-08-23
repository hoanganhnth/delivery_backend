package com.delivery.delivery_service.service;

import com.delivery.delivery_service.common.constants.RoleConstants;
import com.delivery.delivery_service.common.constants.ShipperActionConstants;
import com.delivery.delivery_service.dto.event.ShipperAcceptedEvent;
import com.delivery.delivery_service.dto.request.AcceptBatchRequest;
import com.delivery.delivery_service.dto.response.DeliveryResponse;
import com.delivery.delivery_service.dto.response.DeliveryBatchOfferResponse;
import com.delivery.delivery_service.dto.response.DeliveryOfferResponse;
import com.delivery.delivery_service.entity.Delivery;
import com.delivery.delivery_service.entity.DeliveryBatch;
import com.delivery.delivery_service.entity.DeliveryBatchItem;
import com.delivery.delivery_service.entity.DeliveryBatchItemStatus;
import com.delivery.delivery_service.entity.DeliveryBatchStatus;
import com.delivery.delivery_service.entity.DeliveryStatus;
import com.delivery.delivery_service.exception.AccessDeniedException;
import com.delivery.delivery_service.exception.InvalidStatusException;
import com.delivery.delivery_service.mapper.DeliveryMapper;
import com.delivery.delivery_service.repository.DeliveryBatchItemRepository;
import com.delivery.delivery_service.repository.DeliveryBatchRepository;
import com.delivery.delivery_service.repository.DeliveryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Atomic accept/rollback boundary: a shipper accepts every item or none. */
@Service
public class DeliveryBatchAcceptanceService {
    @Value("${delivery.batch.enabled:false}")
    private boolean batchEnabled;
    private final DeliveryBatchRepository batchRepository;
    private final DeliveryBatchItemRepository itemRepository;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryMapper deliveryMapper;
    private final DeliveryEventPublisher eventPublisher;
    private final OutboxService outboxService;

    public DeliveryBatchAcceptanceService(DeliveryBatchRepository batchRepository,
                                          DeliveryBatchItemRepository itemRepository,
                                          DeliveryRepository deliveryRepository,
                                          DeliveryMapper deliveryMapper,
                                          DeliveryEventPublisher eventPublisher,
                                          OutboxService outboxService) {
        this.batchRepository = batchRepository;
        this.itemRepository = itemRepository;
        this.deliveryRepository = deliveryRepository;
        this.deliveryMapper = deliveryMapper;
        this.eventPublisher = eventPublisher;
        this.outboxService = outboxService;
    }

    @Transactional
    public DeliveryResponse accept(AcceptBatchRequest request, Long shipperId, String role) {
        if (!batchEnabled) throw new InvalidStatusException("Delivery batch dispatch is disabled");
        if (!RoleConstants.SHIPPER.equals(role)) throw new AccessDeniedException("Chỉ shipper mới có thể nhận batch");
        if (request == null || request.getBatchId() == null || shipperId == null || shipperId <= 0) {
            throw new InvalidStatusException("Batch ID and shipper are required");
        }
        DeliveryBatch batch = batchRepository.findByIdForUpdate(request.getBatchId())
                .orElseThrow(() -> new InvalidStatusException("Không tìm thấy batch offer"));
        if (!shipperId.equals(batch.getShipperId())) throw new AccessDeniedException("Batch không thuộc shipper này");
        if (batch.getStatus() == DeliveryBatchStatus.ACCEPTED) {
            return firstResponse(request.getBatchId());
        }
        if (batch.getStatus() != DeliveryBatchStatus.OFFERED
                || batch.getOfferExpiresAt() == null
                || !batch.getOfferExpiresAt().isAfter(LocalDateTime.now())) {
            throw new InvalidStatusException("Batch offer đã hết hạn hoặc không còn hợp lệ");
        }
        List<DeliveryBatchItem> items = itemRepository.findByBatchIdOrderByPickupSequenceAsc(request.getBatchId());
        if (items.isEmpty() || items.size() > 3) throw new InvalidStatusException("Batch không có item hợp lệ");
        List<Delivery> deliveries = items.stream().map(item -> deliveryRepository.findByIdForUpdate(item.getDeliveryId())
                .orElseThrow(() -> new InvalidStatusException("Batch delivery không tồn tại"))).toList();
        for (Delivery delivery : deliveries) {
            if (!DeliveryStatus.WAIT_SHIPPER_CONFIRM.equals(delivery.getStatus())
                    || !shipperId.equals(delivery.getOfferedShipperId())) {
                throw new InvalidStatusException("Batch có delivery không còn ở trạng thái offer");
            }
        }
        LocalDateTime now = LocalDateTime.now();
        for (Delivery delivery : deliveries) {
            delivery.setShipperId(shipperId);
            delivery.setStatus(DeliveryStatus.ASSIGNED);
            delivery.setAssignedAt(now);
            delivery.setOfferExpiresAt(null);
            delivery.setUpdatedAt(now);
            if (request.getCurrentLat() != null && request.getCurrentLng() != null) {
                delivery.setShipperCurrentLat(request.getCurrentLat());
                delivery.setShipperCurrentLng(request.getCurrentLng());
            }
            deliveryRepository.save(delivery);
            eventPublisher.publishShipperStatusChange(shipperId, "BUSY", delivery.getId(), delivery.getOrderId(), batch.getBatchId());
            ShipperAcceptedEvent accepted = ShipperAcceptedEvent.builder()
                    .orderId(delivery.getOrderId()).deliveryId(delivery.getId()).shipperId(shipperId)
                    .notes(request.getNotes()).build();
            eventPublisher.publishShipperAcceptedEvent(accepted);
        }
        items.forEach(item -> {
            item.setItemStatus(DeliveryBatchItemStatus.ACCEPTED);
            item.setUpdatedAt(now);
        });
        itemRepository.saveAll(items);
        batch.setStatus(DeliveryBatchStatus.ACCEPTED);
        batch.setAcceptedAt(now);
        batch.setUpdatedAt(now);
        batchRepository.saveAndFlush(batch);
        publishHoldTransition(batch, "COMMITTED", com.delivery.delivery_service.common.constants.KafkaTopicConstants.BATCH_ACCEPTED_TOPIC);
        return deliveryMapper.deliveryToDeliveryResponse(deliveries.get(0));
    }

    @Transactional(readOnly = true)
    public DeliveryBatchOfferResponse currentOffer(Long shipperId, String role) {
        if (!batchEnabled) return null;
        if (!com.delivery.delivery_service.common.constants.RoleConstants.SHIPPER.equals(role)
                || shipperId == null || shipperId <= 0) {
            throw new com.delivery.delivery_service.exception.AccessDeniedException("Chỉ shipper mới có thể xem batch offer");
        }
        DeliveryBatch batch = batchRepository.findCurrentOffersByShipper(shipperId, LocalDateTime.now(),
                org.springframework.data.domain.PageRequest.of(0, 1)).stream().findFirst().orElse(null);
        if (batch == null) return null;
        List<DeliveryOfferResponse> offers = itemRepository.findByBatchIdOrderByPickupSequenceAsc(batch.getBatchId()).stream()
                .map(item -> deliveryRepository.findById(item.getDeliveryId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(deliveryMapper::deliveryToOfferResponse)
                .toList();
        DeliveryBatchOfferResponse response = new DeliveryBatchOfferResponse();
        response.setBatchId(batch.getBatchId());
        response.setExpiresAt(batch.getOfferExpiresAt());
        response.setOffers(offers);
        return response;
    }

    private void publishHoldTransition(DeliveryBatch batch, String target, String topic) {
        List<String> holdIds = batch.getCodHoldIds() == null || batch.getCodHoldIds().isBlank()
                ? List.of() : List.of(batch.getCodHoldIds().split(","));
        UUID eventId = UUID.nameUUIDFromBytes(("batch-hold:" + target + ":" + batch.getBatchId())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        outboxService.saveEvent(eventId, "DELIVERY_BATCH", batch.getBatchId().toString(),
                "BATCH_COD_HOLD_" + target, topic, batch.getBatchId().toString(),
                Map.of("batchId", batch.getBatchId().toString(), "holdIds", holdIds, "target", target));
    }

    private DeliveryResponse firstResponse(java.util.UUID batchId) {
        DeliveryBatchItem item = itemRepository.findByBatchIdOrderByPickupSequenceAsc(batchId).stream()
                .findFirst().orElseThrow(() -> new InvalidStatusException("Batch không có item"));
        Delivery delivery = deliveryRepository.findById(item.getDeliveryId())
                .orElseThrow(() -> new InvalidStatusException("Batch delivery không tồn tại"));
        return deliveryMapper.deliveryToDeliveryResponse(delivery);
    }
}
