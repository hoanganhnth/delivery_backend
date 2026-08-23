package com.delivery.delivery_service.service;

import com.delivery.delivery_service.common.constants.KafkaTopicConstants;
import com.delivery.delivery_service.dto.event.ShipperFoundEvent;
import com.delivery.delivery_service.dto.event.OfferPersistedEvent;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Durable, all-or-nothing persistence boundary for a proposed shipper batch. */
@Service
public class DeliveryBatchOfferService {

    @Value("${delivery.batch.enabled:false}")
    private boolean batchEnabled;

    private final DeliveryRepository deliveryRepository;
    private final DeliveryBatchRepository batchRepository;
    private final DeliveryBatchItemRepository itemRepository;
    private final OutboxService outboxService;
    private final DeliveryEventPublisher eventPublisher;
    private final Clock clock = Clock.systemDefaultZone();

    public DeliveryBatchOfferService(DeliveryRepository deliveryRepository,
                                     DeliveryBatchRepository batchRepository,
                                     DeliveryBatchItemRepository itemRepository,
                                     OutboxService outboxService,
                                     DeliveryEventPublisher eventPublisher) {
        this.deliveryRepository = deliveryRepository;
        this.batchRepository = batchRepository;
        this.itemRepository = itemRepository;
        this.outboxService = outboxService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void apply(ShipperFoundEvent event) {
        if (!batchEnabled) throw new InvalidStatusException("Delivery batch dispatch is disabled");
        if (event == null || !Boolean.TRUE.equals(event.getBatchOffer()) || event.getBatchId() == null
                || event.getBatchItems() == null || event.getBatchItems().isEmpty()
                || event.getBatchItems().size() > 3 || event.getAvailableShippers() == null
                || event.getAvailableShippers().size() != 1
                || event.getAvailableShippers().get(0).getShipperId() == null) {
            throw new InvalidStatusException("Invalid batch shipper offer event");
        }
        Set<Long> uniqueDeliveries = new HashSet<>();
        Set<Integer> pickupSequences = new HashSet<>();
        Set<Integer> dropoffSequences = new HashSet<>();
        for (ShipperFoundEvent.BatchItem item : event.getBatchItems()) {
            if (item == null || item.getDeliveryId() == null || item.getOrderId() == null
                    || item.getMatchingSessionId() == null
                    || item.getPickupSequence() == null || item.getPickupSequence() < 0
                    || item.getDropoffSequence() == null || item.getDropoffSequence() < 0
                    || item.getPickupSequence() > item.getDropoffSequence()
                    || !uniqueDeliveries.add(item.getDeliveryId())
                    || !pickupSequences.add(item.getPickupSequence())
                    || !dropoffSequences.add(item.getDropoffSequence())) {
                throw new InvalidStatusException("Batch delivery IDs must be unique and complete");
            }
        }
        if (pickupSequences.size() != event.getBatchItems().size()
                || dropoffSequences.size() != event.getBatchItems().size()
                || pickupSequences.stream().anyMatch(sequence -> sequence >= event.getBatchItems().size())
                || dropoffSequences.stream().anyMatch(sequence -> sequence >= event.getBatchItems().size())) {
            throw new InvalidStatusException("Batch route sequences must be contiguous and bounded");
        }
        List<ShipperFoundEvent.BatchItem> orderedItems = event.getBatchItems().stream()
                .sorted(Comparator.comparing(ShipperFoundEvent.BatchItem::getPickupSequence)
                        .thenComparing(ShipperFoundEvent.BatchItem::getDropoffSequence))
                .toList();
        Long shipperId = event.getAvailableShippers().get(0).getShipperId();
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime foundAt = event.getFoundAt() == null ? now : event.getFoundAt();
        int timeout = event.getWaitingTimeoutSeconds() == null ? 180
                : Math.max(1, Math.min(event.getWaitingTimeoutSeconds(), 180));
        LocalDateTime expiresAt = foundAt.plusSeconds(timeout);
        if (!expiresAt.isAfter(now)) throw new InvalidStatusException("Batch shipper offer already expired");

        DeliveryBatch batch = batchRepository.findByIdForUpdate(event.getBatchId()).orElse(null);
        if (batch != null) {
            if (!shipperId.equals(batch.getShipperId()) || batch.getStatus() != DeliveryBatchStatus.OFFERED) {
                throw new InvalidStatusException("Batch offer replay conflicts with existing batch");
            }
            publishOfferPersisted(event, shipperId, expiresAt);
            return;
        }

        batch = new DeliveryBatch();
        batch.setBatchId(event.getBatchId());
        batch.setShipperId(shipperId);
        batch.setStatus(DeliveryBatchStatus.OFFERED);
        batch.setOfferExpiresAt(expiresAt);
        batch.setRouteVersion(1);
        batch.setTotalCodAmount(BigDecimal.ZERO);
        batch.setWaveNumber(event.getBatchWave() == null ? 0 : Math.max(0, event.getBatchWave()));
        if (event.getCodHoldIds() == null || event.getCodHoldIds().size() != event.getBatchItems().size()) {
            throw new InvalidStatusException("Batch COD holds are incomplete");
        }
        batch.setCodHoldIds(event.getCodHoldIds().stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(",")));
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        batchRepository.save(batch);

        for (ShipperFoundEvent.BatchItem item : orderedItems) {
            Delivery delivery = deliveryRepository.findByIdForUpdate(item.getDeliveryId())
                    .orElseThrow(() -> new InvalidStatusException("Delivery is missing from batch"));
            if (!item.getOrderId().equals(delivery.getOrderId())) {
                throw new InvalidStatusException("Batch order does not match delivery");
            }
            if (delivery.getBatchId() != null || (!DeliveryStatus.FINDING_SHIPPER.equals(delivery.getStatus())
                    && !DeliveryStatus.WAIT_SHIPPER_CONFIRM.equals(delivery.getStatus()))) {
                throw new InvalidStatusException("Delivery is not available for batch assignment");
            }
            delivery.setBatchId(event.getBatchId());
            delivery.setBatchSequence(item.getPickupSequence());
            delivery.setOfferedShipperId(shipperId);
            delivery.setOfferExpiresAt(expiresAt);
            delivery.setOfferedMatchingSessionId(item.getMatchingSessionId() == null
                    ? event.getMatchingSessionId() : item.getMatchingSessionId().toString());
            delivery.setStatus(DeliveryStatus.WAIT_SHIPPER_CONFIRM);
            delivery.setUpdatedAt(now);
            deliveryRepository.save(delivery);

            DeliveryBatchItem batchItem = new DeliveryBatchItem();
            batchItem.setBatchId(event.getBatchId());
            batchItem.setDeliveryId(delivery.getId());
            batchItem.setPickupSequence(item.getPickupSequence());
            batchItem.setDropoffSequence(item.getDropoffSequence());
            batchItem.setItemStatus(DeliveryBatchItemStatus.OFFERED);
            batchItem.setCreatedAt(now);
            batchItem.setUpdatedAt(now);
            itemRepository.save(batchItem);
            batch.setTotalCodAmount(batch.getTotalCodAmount().add(
                    item.getTotalPrice() == null ? BigDecimal.ZERO : item.getTotalPrice()));
        }
        batch.setUpdatedAt(now);
        batchRepository.saveAndFlush(batch);
        outboxService.saveEvent(UUID.nameUUIDFromBytes(("batch-offered:" + event.getBatchId())
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "DELIVERY_BATCH", event.getBatchId().toString(), "BATCH_SHIPPER_OFFERED",
                KafkaTopicConstants.SHIPPER_OFFERED_TOPIC, event.getBatchId().toString(), event);
        publishOfferPersisted(event, shipperId, expiresAt);
    }

    private void publishOfferPersisted(ShipperFoundEvent event, Long shipperId, LocalDateTime expiresAt) {
        OfferPersistedEvent confirmation = new OfferPersistedEvent();
        confirmation.setSourceCommandEventId(event.getEventId());
        confirmation.setOrderId(event.getOrderId());
        confirmation.setDeliveryId(event.getDeliveryId());
        confirmation.setMatchingSessionId(event.getMatchingSessionId());
        confirmation.setOfferedShipperId(shipperId);
        confirmation.setOfferExpiresAt(expiresAt);
        eventPublisher.publishOfferPersisted(confirmation);
    }
}
