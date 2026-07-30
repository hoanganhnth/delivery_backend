package com.delivery.delivery_service.service.impl;

import com.delivery.delivery_service.common.constants.KafkaTopicConstants;
import com.delivery.delivery_service.common.constants.RoleConstants;
import com.delivery.delivery_service.dto.event.ShipperAcceptedEvent;
import com.delivery.delivery_service.dto.event.OrderCreatedEvent;
import com.delivery.delivery_service.metrics.BusinessMetrics;
import com.delivery.delivery_service.dto.event.OrderCancelledEvent;
import com.delivery.delivery_service.dto.event.ShipperNotFoundEvent;
import com.delivery.delivery_service.dto.event.ShipperFoundEvent;
import com.delivery.delivery_service.dto.event.DeliveryCompletedEvent;
import com.delivery.delivery_service.dto.event.ExpireShipperOfferCommand;
import com.delivery.delivery_service.common.constants.ShipperActionConstants;
import com.delivery.delivery_service.dto.request.AcceptDeliveryRequest;
import com.delivery.delivery_service.dto.response.DeliveryResponse;
import com.delivery.delivery_service.dto.response.DeliveryOfferResponse;
import com.delivery.delivery_service.entity.Delivery;
import com.delivery.delivery_service.entity.DeliveryStatus;
import com.delivery.delivery_service.exception.AccessDeniedException;
import com.delivery.delivery_service.exception.InvalidStatusException;
import com.delivery.delivery_service.exception.ResourceNotFoundException;
import com.delivery.delivery_service.mapper.DeliveryMapper;
import com.delivery.delivery_service.repository.DeliveryRepository;
import com.delivery.delivery_service.service.DeliveryService;
import com.delivery.delivery_service.service.DeliveryEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.List;

@Slf4j
@Service
public class DeliveryServiceImpl implements DeliveryService {

    private final BusinessMetrics businessMetrics;

    private final DeliveryRepository deliveryRepository;
    private final DeliveryMapper deliveryMapper;
    private final DeliveryEventPublisher deliveryEventPublisher;
    private final com.delivery.delivery_service.service.OutboxService outboxService;

    // ✅ Constructor Injection Pattern
    public DeliveryServiceImpl(DeliveryRepository deliveryRepository,
            DeliveryMapper deliveryMapper,
            DeliveryEventPublisher deliveryEventPublisher,
            com.delivery.delivery_service.service.OutboxService outboxService,
            BusinessMetrics businessMetrics) {
        this.deliveryRepository = deliveryRepository;
        this.deliveryMapper = deliveryMapper;
        this.deliveryEventPublisher = deliveryEventPublisher;
        this.outboxService = outboxService;
        this.businessMetrics = businessMetrics;
    }

    @Override
    @Transactional
    public DeliveryResponse createDeliveryFromOrderEvent(OrderCreatedEvent event) {
        validateCreateDeliveryEvent(event);
        try {
            Delivery existing = deliveryRepository.findByOrderId(event.getOrderId()).orElse(null);
            if (existing != null) {
                requireMatchingCreateCommand(existing, event);
                log.info("Create-delivery command already applied for order {}, returning delivery {}",
                        event.getOrderId(), existing.getId());
                return deliveryMapper.deliveryToDeliveryResponse(existing);
            }

            // ✅ Tự động tạo delivery record từ OrderCreatedEvent theo Backend Instructions
            Delivery delivery = new Delivery();
            delivery.setCreateEventId(event.getEventId());

            // Set basic order info
            delivery.setOrderId(event.getOrderId());
            // Note: shipperId sẽ được set sau khi có shipper assignment

            // Set pickup location (restaurant)
            delivery.setPickupAddress(event.getRestaurantAddress());

            // ✅ Set default pickup coordinates (có thể improve sau bằng geocoding)

            // Fallback: TP.HCM center coordinates
            delivery.setPickupLat(event.getPickupLat());
            delivery.setPickupLng(event.getPickupLng());

            // Set delivery location
            delivery.setDeliveryAddress(event.getDeliveryAddress());
            delivery.setDeliveryLat(event.getDeliveryLat());
            delivery.setDeliveryLng(event.getDeliveryLng());

            // Shipping fee is server-owned by Order and validated at the Kafka boundary.
            // Delivery must never invent a financial fallback.
            delivery.setShippingFee(event.getShippingFee());

            // ✅ Set COD info (tổng tiền khách trả + phương thức thanh toán)
            delivery.setTotalPrice(event.getTotalPrice());
            delivery.setPaymentMethod(event.getPaymentMethod());

            // Set notes
            delivery.setNotes(event.getNotes());

            // Set initial status - FINDING_SHIPPER (tự động tìm shipper)
            delivery.setStatus(DeliveryStatus.FINDING_SHIPPER);

            // Set timestamps
            delivery.setCreatedAt(LocalDateTime.now());
            delivery.setUpdatedAt(LocalDateTime.now());
            // Delivery ownership belongs to the customer who placed the order.
            // OrderCreatedEvent.creatorId is the restaurant owner and must never
            // be used for customer authorization or notifications.
            delivery.setCreatorId(event.getUserId());
            delivery.setRestaurantId(event.getRestaurantId());
            delivery.setRestaurantOwnerId(event.getCreatorId());

            // shipperId sẽ là null cho đến khi được assign
            // delivery.setShipperId(null); // default is null

            // Save delivery
            Delivery savedDelivery = deliveryRepository.save(delivery);

            // ✅ PHÁT LỆNH: Lưu kết quả vào Outbox để OutboxRelay gửi cho Saga
            // Saga sẽ nhận event [delivery.created.result] để tiếp tục luồng FIND_SHIPPER
            java.util.Map<String, Object> result = new java.util.HashMap<>();
            result.put("orderId", savedDelivery.getOrderId());
            result.put("deliveryId", savedDelivery.getId());
            result.put("status", savedDelivery.getStatus().name());
            result.put("pickupAddress", savedDelivery.getPickupAddress());
            result.put("pickupLat", savedDelivery.getPickupLat());
            result.put("pickupLng", savedDelivery.getPickupLng());
            result.put("deliveryAddress", savedDelivery.getDeliveryAddress());
            result.put("deliveryLat", savedDelivery.getDeliveryLat());
            result.put("deliveryLng", savedDelivery.getDeliveryLng());
            result.put("totalPrice", savedDelivery.getTotalPrice());
            result.put("shippingFee", savedDelivery.getShippingFee());
            result.put("paymentMethod", savedDelivery.getPaymentMethod());
            result.put("restaurantId", savedDelivery.getRestaurantId());

            outboxService.saveEvent("DELIVERY", savedDelivery.getId().toString(), "DELIVERY_CREATED_RESULT",
                    "delivery.created.result", savedDelivery.getOrderId().toString(), result);
            log.info("✅ [Delivery] Stored creation result in outbox for orderId={}, deliveryId={}",
                    savedDelivery.getOrderId(), savedDelivery.getId());

            return deliveryMapper.deliveryToDeliveryResponse(savedDelivery);

        } catch (Exception e) {
            throw new RuntimeException("Failed to create delivery from order event: " + e.getMessage(), e);
        }
    }

    private void requireMatchingCreateCommand(Delivery existing, OrderCreatedEvent event) {
        boolean matches = Objects.equals(existing.getCreateEventId(), event.getEventId())
                && Objects.equals(existing.getCreatorId(), event.getUserId())
                && Objects.equals(existing.getRestaurantId(), event.getRestaurantId())
                && Objects.equals(existing.getRestaurantOwnerId(), event.getCreatorId())
                && Objects.equals(existing.getPickupAddress(), event.getRestaurantAddress())
                && Objects.equals(existing.getPickupLat(), event.getPickupLat())
                && Objects.equals(existing.getPickupLng(), event.getPickupLng())
                && Objects.equals(existing.getDeliveryAddress(), event.getDeliveryAddress())
                && Objects.equals(existing.getDeliveryLat(), event.getDeliveryLat())
                && Objects.equals(existing.getDeliveryLng(), event.getDeliveryLng())
                && sameAmount(existing.getShippingFee(), event.getShippingFee())
                && sameAmount(existing.getTotalPrice(), event.getTotalPrice())
                && Objects.equals(existing.getPaymentMethod(), event.getPaymentMethod());
        if (!matches) {
            throw new InvalidStatusException("Create-delivery replay conflicts with existing delivery "
                    + existing.getId() + " for order " + event.getOrderId());
        }
    }

    private boolean sameAmount(BigDecimal left, BigDecimal right) {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    @Override
    @Transactional
    public DeliveryResponse acceptDelivery(AcceptDeliveryRequest request, Long shipperId, String role) {
        log.info("🚚 Shipper {} attempting to accept order {}", shipperId, request.getOrderId());

        // ✅ Validate shipper role
        if (!RoleConstants.SHIPPER.equals(role)) {
            throw new AccessDeniedException("Chỉ shipper mới có thể nhận đơn hàng");
        }

        // ✅ Validate request
        if (request.getOrderId() == null) {
            throw new InvalidStatusException("Order ID is required");
        }

        // ✅ Validate action
        if (request.getAction() == null ||
                (!ShipperActionConstants.ACCEPT.equals(request.getAction()) &&
                        !ShipperActionConstants.REJECT.equals(request.getAction()))) {
            throw new InvalidStatusException("Action must be ACCEPT or REJECT");
        }

        // ✅ Validate reject reason if rejecting
        if (ShipperActionConstants.REJECT.equals(request.getAction()) &&
                (request.getRejectReason() == null || request.getRejectReason().trim().isEmpty())) {
            throw new InvalidStatusException("Reject reason is required when rejecting delivery");
        }

        // ✅ Validate pickup time if accepting
        // if (ShipperActionConstants.ACCEPT.equals(request.getAction()) &&
        // request.getEstimatedPickupTime() == null) {
        // throw new InvalidStatusException("Estimated pickup time is required when
        // accepting delivery");
        // }

        // ✅ Find delivery by order ID
        Delivery delivery = deliveryRepository.findByOrderIdForUpdate(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy thông tin giao hàng cho đơn hàng: " + request.getOrderId()));

        if (ShipperActionConstants.ACCEPT.equals(request.getAction())
                && DeliveryStatus.ASSIGNED.equals(delivery.getStatus())
                && shipperId.equals(delivery.getShipperId())) {
            log.info("Shipper acceptance already applied for order {}, skipping duplicate", request.getOrderId());
            return deliveryMapper.deliveryToDeliveryResponse(delivery);
        }

        if (ShipperActionConstants.REJECT.equals(request.getAction())
                && isRejectedShipperReplay(delivery, shipperId, request.getRejectReason())) {
            log.info("Shipper rejection already applied for order {}, skipping duplicate", request.getOrderId());
            return deliveryMapper.deliveryToDeliveryResponse(delivery);
        }

        // ✅ Validate delivery status
        if (!DeliveryStatus.WAIT_SHIPPER_CONFIRM.equals(delivery.getStatus())) {
            throw new InvalidStatusException("Đơn hàng không ở trạng thái chờ shipper xác nhận");
        }

        if (delivery.getOfferedShipperId() == null || !delivery.getOfferedShipperId().equals(shipperId)) {
            throw new AccessDeniedException("Đơn hàng này không được offer cho shipper hiện tại");
        }
        if (delivery.getOfferExpiresAt() == null || !delivery.getOfferExpiresAt().isAfter(LocalDateTime.now())) {
            throw new InvalidStatusException("Offer nhận đơn đã hết hạn");
        }

        // This check remains a business guard. The row lock above prevents two
        // shippers accepting this order; a database-level per-shipper guard is a
        // separate hardening item.
        if (ShipperActionConstants.ACCEPT.equals(request.getAction())) {
            List<Delivery> activeDeliveries = deliveryRepository.findActiveDeliveriesByShipper(
                    shipperId, org.springframework.data.domain.PageRequest.of(0, 1));
            if (activeDeliveries != null && !activeDeliveries.isEmpty()) {
                log.warn("⚠️ Shipper {} attempted to accept order {} but already has {} active delivery(ies)",
                        shipperId, request.getOrderId(), activeDeliveries.size());
                throw new InvalidStatusException(
                        "Bạn đang có đơn hàng đang xử lý (Delivery #" + activeDeliveries.get(0).getId()
                                + "). Hãy hoàn thành đơn hiện tại trước khi nhận đơn mới!");
            }
        }

        // ✅ Check if already assigned to another shipper
        if (delivery.getShipperId() != null && !delivery.getShipperId().equals(shipperId)) {
            throw new InvalidStatusException("Đơn hàng đã được giao cho shipper khác");
        }

        // ✅ Process based on action
        if (ShipperActionConstants.ACCEPT.equals(request.getAction())) {
            // ACCEPT logic
            delivery.setShipperId(shipperId);
            delivery.setStatus(DeliveryStatus.ASSIGNED);
            businessMetrics.record("delivery_assigned");
            delivery.setAssignedAt(LocalDateTime.now());
            delivery.setOfferExpiresAt(null);
            delivery.setUpdatedAt(LocalDateTime.now());

            // Update shipper location nếu có
            if (request.getCurrentLat() != null && request.getCurrentLng() != null) {
                delivery.setShipperCurrentLat(request.getCurrentLat());
                delivery.setShipperCurrentLng(request.getCurrentLng());
            }

            // Update estimated pickup time
            // estimatedPickupTime is accepted for client compatibility, but it
            // must not be presented as a delivery ETA. No ETA calculator exists
            // in the MVP contract, so the persisted ETA remains null.

            log.info("✅ Shipper {} ACCEPTED order {}", shipperId, request.getOrderId());

        } else if (ShipperActionConstants.REJECT.equals(request.getAction())) {
            // REJECT logic - không assign shipper, reset lại status để tìm shipper mới
            delivery.setShipperId(null);
            delivery.setStatus(DeliveryStatus.FINDING_SHIPPER);
            delivery.setOfferedShipperId(shipperId);
            delivery.setOfferExpiresAt(null);
            delivery.setUpdatedAt(LocalDateTime.now());
            delivery.setRejectReason(request.getRejectReason());

            log.info("❌ Shipper {} REJECTED order {} - Reason: {} → Status reset to FINDING_SHIPPER for re-assignment",
                    shipperId, request.getOrderId(), request.getRejectReason());
        }

        // Update notes nếu có
        if (request.getNotes() != null && !request.getNotes().trim().isEmpty()) {
            String existingNotes = delivery.getNotes() != null ? delivery.getNotes() : "";
            delivery.setNotes(existingNotes + " | Shipper notes: " + request.getNotes());
        }

        Delivery savedDelivery;
        try {
            // Flush ACCEPT before publishing any event so the partial unique index
            // can atomically enforce one active delivery per shipper.
            savedDelivery = ShipperActionConstants.ACCEPT.equals(request.getAction())
                    ? deliveryRepository.saveAndFlush(delivery)
                    : deliveryRepository.save(delivery);
        } catch (DataIntegrityViolationException e) {
            throw new InvalidStatusException(
                    "Shipper already has another active delivery");
        }

        // ✅ Publish event based on action
        if (ShipperActionConstants.ACCEPT.equals(request.getAction())) {
            deliveryEventPublisher.publishShipperStatusChange(
                    shipperId, "BUSY", savedDelivery.getId(), savedDelivery.getOrderId());
            publishMatchAcceptedEvent(savedDelivery, shipperId, request);
            log.info("✅ Delivery {} ACCEPTED successfully by shipper {}", delivery.getId(), shipperId);
        } else if (ShipperActionConstants.REJECT.equals(request.getAction())) {
            publishMatchRejectedEvent(savedDelivery, shipperId, request);
            deliveryEventPublisher.publishShipperStatusChange(
                    shipperId, "AVAILABLE", savedDelivery.getId(), savedDelivery.getOrderId());
            log.info("❌ Delivery {} REJECTED by shipper {} - Reason: {}",
                    delivery.getId(), shipperId, request.getRejectReason());
        }

        DeliveryResponse response = deliveryMapper.deliveryToDeliveryResponse(savedDelivery);

        return response;
    }

    @Override
    @Transactional
    public void cacheShipperOffer(ShipperFoundEvent event) {
        if (event == null || event.getEventId() == null
                || event.getDeliveryId() == null || event.getDeliveryId() <= 0
                || event.getOrderId() == null || event.getOrderId() <= 0
                || event.getAvailableShippers() == null || event.getAvailableShippers().size() != 1
                || event.getAvailableShippers().get(0).getShipperId() == null) {
            throw new InvalidStatusException("Invalid single-shipper offer event");
        }

        Delivery delivery = deliveryRepository.findByOrderIdForUpdate(event.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy delivery cho order: " + event.getOrderId()));

        if (!delivery.getId().equals(event.getDeliveryId())) {
            throw new InvalidStatusException("Delivery ID does not match order ID");
        }
        Long offeredShipperId = event.getAvailableShippers().get(0).getShipperId();
        LocalDateTime now = LocalDateTime.now();
        int timeoutSeconds = event.getWaitingTimeoutSeconds() == null
                ? 180
                : Math.max(1, Math.min(event.getWaitingTimeoutSeconds(), 180));
        LocalDateTime foundAt = event.getFoundAt() == null ? now : event.getFoundAt();
        LocalDateTime expiresAt = foundAt.plusSeconds(timeoutSeconds);
        if (!expiresAt.isAfter(now)) {
            throw new InvalidStatusException("Shipper offer already expired");
        }

        boolean replacingExpiredOffer = DeliveryStatus.WAIT_SHIPPER_CONFIRM.equals(delivery.getStatus())
                && delivery.getOfferExpiresAt() != null
                && !delivery.getOfferExpiresAt().isAfter(now);
        if (delivery.getOfferedShipperId() != null
                && !delivery.getOfferedShipperId().equals(offeredShipperId)
                && delivery.getOfferExpiresAt() != null
                && delivery.getOfferExpiresAt().isAfter(now)) {
            throw new InvalidStatusException("Delivery already has an active shipper offer");
        }
        if (offeredShipperId.equals(delivery.getOfferedShipperId())
                && sameOfferDeadline(expiresAt, delivery.getOfferExpiresAt())) {
            if (!DeliveryStatus.WAIT_SHIPPER_CONFIRM.equals(delivery.getStatus())) {
                throw new InvalidStatusException("Persisted offer has contradictory delivery status");
            }
            log.info("Shipper offer already applied for delivery {}, skipping duplicate", delivery.getId());
            return;
        }
        if (!DeliveryStatus.FINDING_SHIPPER.equals(delivery.getStatus()) && !replacingExpiredOffer) {
            throw new InvalidStatusException("Delivery is no longer finding a shipper");
        }

        delivery.setOfferedShipperId(offeredShipperId);
        delivery.setOfferExpiresAt(expiresAt);
        delivery.setStatus(DeliveryStatus.WAIT_SHIPPER_CONFIRM);
        delivery.setUpdatedAt(now);
        deliveryRepository.saveAndFlush(delivery);

        // Notification consumes only this topic, so the offer is visible in the
        // database before the selected shipper sees it.
        outboxService.saveEvent("DELIVERY", delivery.getId().toString(), "SHIPPER_OFFERED",
                KafkaTopicConstants.SHIPPER_OFFERED_TOPIC, delivery.getOrderId().toString(), event);
        log.info("📤 Persisted offer for delivery {} to shipper {} until {}",
                delivery.getId(), offeredShipperId, expiresAt);
    }

    @Override
    @Transactional
    public void expireShipperOffer(ExpireShipperOfferCommand command) {
        if (command == null || command.getEventId() == null
                || !positive(command.getOrderId()) || !positive(command.getDeliveryId())
                || !positive(command.getTimedOutShipperId())
                || command.getExpectedOfferExpiresAt() == null) {
            throw new InvalidStatusException("Invalid expire-shipper-offer command");
        }

        Delivery delivery = deliveryRepository.findByOrderIdForUpdate(command.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy delivery cho order: " + command.getOrderId()));
        if (!command.getDeliveryId().equals(delivery.getId())) {
            throw new InvalidStatusException("Delivery ID does not match order ID");
        }

        if (DeliveryStatus.FINDING_SHIPPER.equals(delivery.getStatus())
                && delivery.getOfferedShipperId() == null && delivery.getOfferExpiresAt() == null) {
            log.info("Offer timeout already applied for delivery {}, skipping replay", delivery.getId());
            return;
        }

        // A delayed timeout must never clear an accepted or newer offer.
        if (!DeliveryStatus.WAIT_SHIPPER_CONFIRM.equals(delivery.getStatus())
                || !command.getTimedOutShipperId().equals(delivery.getOfferedShipperId())
                || !sameOfferDeadline(command.getExpectedOfferExpiresAt(), delivery.getOfferExpiresAt())) {
            log.info("Skipping stale offer-timeout command for delivery {}", delivery.getId());
            return;
        }
        if (delivery.getOfferExpiresAt().isAfter(LocalDateTime.now())) {
            throw new InvalidStatusException("Cannot expire a shipper offer before its deadline");
        }

        delivery.setOfferedShipperId(null);
        delivery.setOfferExpiresAt(null);
        delivery.setStatus(DeliveryStatus.FINDING_SHIPPER);
        delivery.setUpdatedAt(LocalDateTime.now());
        deliveryRepository.save(delivery);
        log.info("Expired shipper offer for delivery {}, Saga may rematch", delivery.getId());
    }

    @Override
    @Transactional
    public DeliveryResponse cancelAssignedDelivery(Long orderId, Long shipperId, String role, String reason) {
        log.info("🔄 Shipper {} requesting to cancel assigned order {}", shipperId, orderId);

        // ✅ Chỉ shipper mới được huỷ đơn của mình
        if (!RoleConstants.SHIPPER.equals(role)) {
            throw new AccessDeniedException("Chỉ shipper mới có thể huỷ đơn đã nhận");
        }
        if (orderId == null) {
            throw new InvalidStatusException("Order ID is required");
        }

        // Serialize against pickup/status transitions and competing cancellation.
        Delivery delivery = deliveryRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy thông tin giao hàng cho đơn hàng: " + orderId));

        String cancelNote = canonicalCancelAssignmentReason(reason);
        if (isRejectedShipperReplay(delivery, shipperId, cancelNote)) {
            log.info("Shipper cancellation already applied for order {}, skipping duplicate", orderId);
            return deliveryMapper.deliveryToDeliveryResponse(delivery);
        }

        // ✅ Chỉ shipper đang được gán mới được huỷ
        if (delivery.getShipperId() == null || !delivery.getShipperId().equals(shipperId)) {
            throw new AccessDeniedException("Bạn không phải shipper được gán cho đơn này");
        }

        // ✅ Chỉ cho huỷ TRƯỚC khi lấy hàng (ASSIGNED). Sau PICKED_UP hàng đã ở shipper
        //    → cần quy trình khác (xem docs/product/features/delivery-matching.md §11).
        if (!DeliveryStatus.ASSIGNED.equals(delivery.getStatus())) {
            throw new InvalidStatusException(
                    "Chỉ có thể huỷ đơn khi chưa lấy hàng (trạng thái ASSIGNED). Hiện tại: "
                            + delivery.getStatus());
        }

        // ✅ Reset đơn về tìm shipper mới, giải phóng shipper hiện tại
        delivery.setShipperId(null);
        delivery.setStatus(DeliveryStatus.FINDING_SHIPPER);
        delivery.setOfferedShipperId(shipperId);
        delivery.setOfferExpiresAt(null);
        delivery.setUpdatedAt(LocalDateTime.now());
        delivery.setRejectReason(cancelNote);
        String existingNotes = delivery.getNotes() != null ? delivery.getNotes() : "";
        delivery.setNotes(existingNotes + " | Shipper " + shipperId + " huỷ sau accept: " + cancelNote);

        Delivery savedDelivery = deliveryRepository.save(delivery);

        // ✅ Giải phóng shipper (đánh dấu rảnh)
        deliveryEventPublisher.publishShipperStatusChange(
                shipperId, "AVAILABLE", savedDelivery.getId(), savedDelivery.getOrderId());

        // ✅ Re-trigger tìm shipper mới qua CÙNG cơ chế rematch của Saga
        //    (Saga sẽ gom rejectedShipperId vào excludedShipperIds + áp giới hạn số lần).
        publishShipperRejectedForRematch(savedDelivery, shipperId, cancelNote);

        log.info("✅ Order {} reset to FINDING_SHIPPER after shipper {} cancelled", orderId, shipperId);

        DeliveryResponse response = deliveryMapper.deliveryToDeliveryResponse(savedDelivery);
        return response;
    }

    /**
     * ✅ Bắn event lên topic 'delivery.shipper-rejected' để Saga re-trigger tìm shipper
     * mới. Dùng chung cho cả REJECT (trước accept) và CANCEL (sau accept).
     */
    private void publishShipperRejectedForRematch(Delivery delivery, Long shipperId, String reason) {
            java.util.Map<String, Object> rejectedEvent = new java.util.HashMap<>();
            rejectedEvent.put("orderId", delivery.getOrderId());
            rejectedEvent.put("deliveryId", delivery.getId());
            rejectedEvent.put("rejectedShipperId", shipperId);
            rejectedEvent.put("rejectReason", reason);
            rejectedEvent.put("pickupAddress", delivery.getPickupAddress());
            rejectedEvent.put("pickupLat", delivery.getPickupLat());
            rejectedEvent.put("pickupLng", delivery.getPickupLng());
            rejectedEvent.put("deliveryAddress", delivery.getDeliveryAddress());
            rejectedEvent.put("deliveryLat", delivery.getDeliveryLat());
            rejectedEvent.put("deliveryLng", delivery.getDeliveryLng());
            rejectedEvent.put("eventType", "SHIPPER_REJECTED");
            rejectedEvent.put("timestamp", System.currentTimeMillis());

            outboxService.saveEvent("DELIVERY", delivery.getId().toString(), "SHIPPER_REJECTED",
                    KafkaTopicConstants.SHIPPER_REJECTED_TOPIC,
                    delivery.getOrderId().toString(), rejectedEvent);

            log.info("📤 Published SHIPPER_REJECTED (rematch) for delivery {}, shipper {}",
                    delivery.getId(), shipperId);
    }

    private boolean isRejectedShipperReplay(Delivery delivery, Long shipperId, String reason) {
        return DeliveryStatus.FINDING_SHIPPER.equals(delivery.getStatus())
                && delivery.getShipperId() == null
                && delivery.getOfferExpiresAt() == null
                && shipperId != null
                && shipperId.equals(delivery.getOfferedShipperId())
                && Objects.equals(delivery.getRejectReason(), reason);
    }

    private String canonicalCancelAssignmentReason(String reason) {
        return reason != null && !reason.trim().isEmpty() ? reason : "Shipper huỷ sau khi nhận";
    }

    @Override
    @Transactional
    public DeliveryResponse updateDeliveryStatus(Long deliveryId, DeliveryStatus status, Long userId, String role) {
        Delivery delivery = deliveryRepository.findByIdForUpdate(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy thông tin giao hàng với ID: " + deliveryId));

        validateShipperStatusUpdatePermission(delivery, userId, role);
        requireShipperStatusTarget(status);

        // A client may retry after the first transaction committed but before it
        // received the response. Return the persisted state without duplicating
        // status/completion outbox events.
        if (status.equals(delivery.getStatus())) {
            return deliveryMapper.deliveryToDeliveryResponse(delivery);
        }

        requireNextShipperStatus(delivery.getStatus(), status);

        // Lưu old status để publish event
        String oldStatus = delivery.getStatus().name();

        applyShipperStatusTransition(delivery, status);

        Delivery updatedDelivery = deliveryRepository.save(delivery);

        // ✅ Publish delivery status update event với orderId
        deliveryEventPublisher.publishDeliveryStatusUpdated(
                deliveryId, delivery.getOrderId(), delivery.getCreatorId(), delivery.getShipperId(),
                status.name(), oldStatus);

        DeliveryResponse response = deliveryMapper.deliveryToDeliveryResponse(updatedDelivery);

        return response;
    }

    @Override
    public DeliveryResponse getDeliveryById(Long deliveryId, Long userId, String role) {
        Delivery delivery = findDeliveryById(deliveryId);
        validateViewPermission(delivery, userId, role);
        return deliveryMapper.deliveryToDeliveryResponse(delivery);
    }

    @Override
    public List<DeliveryResponse> getDeliveriesByShipper(Long shipperId, Long userId, String role) {
        validateShipperListPermission(shipperId, userId, role);

        List<Delivery> deliveries = deliveryRepository.findByShipperIdOrderByCreatedAtDesc(
                shipperId, org.springframework.data.domain.PageRequest.of(0, 100));
        return deliveryMapper.deliveriesToDeliveryResponses(deliveries);
    }

    @Override
    public DeliveryResponse getDeliveryByOrderId(Long orderId, Long userId, String role) {
        Delivery delivery = deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy thông tin giao hàng cho đơn hàng: " + orderId));

        validateViewPermission(delivery, userId, role);
        return deliveryMapper.deliveryToDeliveryResponse(delivery);
    }

    @Override
    public List<DeliveryResponse> getActiveDeliveriesByShipper(Long shipperId, Long userId, String role) {
        validateShipperListPermission(shipperId, userId, role);

        List<Delivery> deliveries = deliveryRepository.findActiveDeliveriesByShipper(
                shipperId, org.springframework.data.domain.PageRequest.of(0, 100));
        return deliveryMapper.deliveriesToDeliveryResponses(deliveries);
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryOfferResponse getCurrentOffer(Long shipperId, String role) {
        if (!RoleConstants.SHIPPER.equals(role)) {
            throw new AccessDeniedException("Chỉ shipper mới có thể xem offer hiện tại");
        }
        if (!positive(shipperId)) {
            throw new InvalidStatusException("Shipper ID is required");
        }

        List<Delivery> offers = deliveryRepository.findCurrentOffersByShipper(
                shipperId, LocalDateTime.now(), org.springframework.data.domain.PageRequest.of(0, 2));
        if (offers.size() > 1) {
            throw new InvalidStatusException("Shipper has multiple active offers");
        }
        return offers.isEmpty() ? null : deliveryMapper.deliveryToOfferResponse(offers.get(0));
    }

    private Delivery findDeliveryById(Long deliveryId) {
        return deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy thông tin giao hàng với ID: " + deliveryId));
    }

    private void validateViewPermission(Delivery delivery, Long userId, String role) {
        if (RoleConstants.ADMIN.equals(role)) {
            return; // Admin có thể xem tất cả
        }

        // Shipper có thể xem delivery của mình
        if (RoleConstants.SHIPPER.equals(role) && userId != null && userId.equals(delivery.getShipperId())) {
            return;
        }

        // creatorId stores the canonical order userId, so users can only read
        // the delivery belonging to their own order.
        if (RoleConstants.USER.equals(role) && userId != null && userId.equals(delivery.getCreatorId())) {
            return;
        }

        // Restaurant owner identity is copied from the server-validated order
        // event. Legacy rows without this field remain fail-closed.
        if (RoleConstants.RESTAURANT_OWNER.equals(role)
                && userId != null
                && userId.equals(delivery.getRestaurantOwnerId())) {
            return;
        }

        throw new AccessDeniedException("Bạn không có quyền xem thông tin giao hàng này");
    }

    private void validateShipperListPermission(Long shipperId, Long userId, String role) {
        if (RoleConstants.ADMIN.equals(role)) {
            return;
        }
        if (RoleConstants.SHIPPER.equals(role)
                && shipperId != null
                && shipperId.equals(userId)) {
            return;
        }
        throw new AccessDeniedException("Bạn không có quyền xem danh sách delivery này");
    }

    private void validateShipperStatusUpdatePermission(Delivery delivery, Long userId, String role) {
        if (RoleConstants.SHIPPER.equals(role)
                && userId != null
                && userId.equals(delivery.getShipperId())) {
            return;
        }

        throw new AccessDeniedException("Chỉ shipper được phân công mới có thể cập nhật trạng thái giao hàng");
    }

    private void requireShipperStatusTarget(DeliveryStatus status) {
        if (status != DeliveryStatus.PICKED_UP
                && status != DeliveryStatus.DELIVERING
                && status != DeliveryStatus.DELIVERED) {
            throw new InvalidStatusException(
                    "Shipper chỉ có thể cập nhật PICKED_UP, DELIVERING hoặc DELIVERED");
        }
    }

    private void requireNextShipperStatus(DeliveryStatus currentStatus, DeliveryStatus requestedStatus) {
        DeliveryStatus expectedStatus = switch (currentStatus) {
            case ASSIGNED -> DeliveryStatus.PICKED_UP;
            case PICKED_UP -> DeliveryStatus.DELIVERING;
            case DELIVERING -> DeliveryStatus.DELIVERED;
            default -> null;
        };
        if (expectedStatus != requestedStatus) {
            throw new InvalidStatusException("Không thể chuyển từ trạng thái " + currentStatus.getDescription()
                    + " sang " + requestedStatus.getDescription());
        }
    }

    private void applyShipperStatusTransition(Delivery delivery, DeliveryStatus status) {
        delivery.setStatus(status);

        // Cập nhật timestamp theo status
        switch (status) {
            case PICKED_UP:
                delivery.setPickedUpAt(LocalDateTime.now());
                break;
            case DELIVERED:
                delivery.setDeliveredAt(LocalDateTime.now());
                businessMetrics.record("delivery_completed");

                // ✅ Publish DeliveryCompletedEvent để tự động cộng tiền cho shipper
                publishDeliveryCompletedEvent(delivery);

                // ✅ Publish event đánh dấu shipper rảnh (thay thế REST call)
                if (delivery.getShipperId() != null) {
                    deliveryEventPublisher.publishShipperStatusChange(
                            delivery.getShipperId(), "AVAILABLE", delivery.getId(), delivery.getOrderId());
                }
                break;
            case DELIVERING:
                // Không cần cập nhật timestamp đặc biệt
                break;
            default:
                throw new IllegalStateException("Unsupported shipper status target: " + status);
        }
    }

    /**
     * ✅ Publish MatchAcceptedEvent để thông báo cho Notification Service
     */
    private void publishMatchAcceptedEvent(Delivery delivery, Long shipperId, AcceptDeliveryRequest request) {
        try {
            ShipperAcceptedEvent event = ShipperAcceptedEvent.builder()
                    .orderId(delivery.getOrderId())
                    .deliveryId(delivery.getId())
                    .shipperId(shipperId)
                    .notes(request.getNotes())
                    .build();

            deliveryEventPublisher.publishShipperAcceptedEvent(event);

            log.info("📤 Published ShipperAcceptedEvent for delivery {}, shipper {}",
                    delivery.getId(), shipperId);

        } catch (Exception e) {
            log.error("💥 Failed to publish MatchAcceptedEvent for delivery {}: {}",
                    delivery.getId(), e.getMessage(), e);
            throw new IllegalStateException("Failed to store MatchAcceptedEvent", e);
        }
    }

    /**
     * ✅ Publish ShipperRejectedEvent khi shipper reject đơn
     * Gửi đến topic riêng 'delivery.shipper-rejected' để Saga re-trigger tìm shipper mới
     */
    private void publishMatchRejectedEvent(Delivery delivery, Long shipperId, AcceptDeliveryRequest request) {
        // Dùng chung cơ chế rematch với cancel-after-accept.
        publishShipperRejectedForRematch(delivery, shipperId, request.getRejectReason());
    }

    /**
     * ✅ Publish DeliveryCompletedEvent để tự động cộng tiền vào shipper balance
     */
    private void publishDeliveryCompletedEvent(Delivery delivery) {
        try {
            if (!positive(delivery.getId()) || !positive(delivery.getOrderId())
                    || !positive(delivery.getRestaurantId()) || !positive(delivery.getShipperId())) {
                throw new IllegalStateException("Completed delivery is missing canonical identity fields");
            }
            if (!"COD".equals(delivery.getPaymentMethod())) {
                throw new IllegalStateException("MVP settlement only accepts COD deliveries");
            }
            if (delivery.getShippingFee() == null
                    || delivery.getShippingFee().compareTo(java.math.BigDecimal.ZERO) <= 0
                    || delivery.getTotalPrice() == null
                    || delivery.getTotalPrice().compareTo(delivery.getShippingFee()) <= 0) {
                throw new IllegalStateException("Completed delivery has invalid canonical totals");
            }

            // ✅ 1. Tính toán phần Shipper (từ Phí Ship)
            // Phí ship: 85% cho Shipper, 15% cho Platform
            java.math.BigDecimal shipperEarnings = com.delivery.delivery_service.common.constants.PricingConstants
                    .calculateShipperEarnings(delivery.getShippingFee());
            java.math.BigDecimal shippingCommission = com.delivery.delivery_service.common.constants.PricingConstants
                    .calculatePlatformCommission(delivery.getShippingFee());

            // ✅ 2. Tính toán phần Nhà hàng (từ Giá món ăn)
            // foodPrice = totalPrice - shippingFee
            java.math.BigDecimal foodPrice = java.math.BigDecimal.ZERO;
            if (delivery.getTotalPrice() != null && delivery.getShippingFee() != null) {
                foodPrice = delivery.getTotalPrice().subtract(delivery.getShippingFee());
            }

            // Hoa hồng từ nhà hàng (ví dụ 20% giá món)
            java.math.BigDecimal restaurantCommission = foodPrice
                    .multiply(com.delivery.delivery_service.common.constants.PricingConstants.RESTAURANT_COMMISSION_RATE);
            // Tiền thực nhận của nhà hàng = Giá món - Hoa hồng
            java.math.BigDecimal restaurantEarnings = foodPrice.subtract(restaurantCommission);

            // ✅ 3. Tổng thu nhập của nền tảng (Platform)
            java.math.BigDecimal totalPlatformEarnings = shippingCommission.add(restaurantCommission);

            log.info(
                    "💰 Settlement calculation for delivery {}: foodPrice={}, shipFee={}, shipperGets={}, restaurantGets={}, platformGets={}",
                    delivery.getId(), foodPrice, delivery.getShippingFee(), shipperEarnings, restaurantEarnings,
                    totalPlatformEarnings);

            DeliveryCompletedEvent event = DeliveryCompletedEvent.builder()
                    .deliveryId(delivery.getId())
                    .orderId(delivery.getOrderId())
                    .shipperId(delivery.getShipperId())
                    .restaurantId(delivery.getRestaurantId())
                    .shippingFee(delivery.getShippingFee())
                    .shipperEarnings(shipperEarnings)
                    .restaurantEarnings(restaurantEarnings)
                    .restaurantCommission(restaurantCommission)
                    .shippingCommission(shippingCommission)
                    .totalPlatformEarnings(totalPlatformEarnings)
                    .deliveredAt(delivery.getDeliveredAt())
                    .deliveryAddress(delivery.getDeliveryAddress())
                    .paymentMethod(delivery.getPaymentMethod())
                    .build();

            deliveryEventPublisher.publishDeliveryCompletedEvent(event);

            log.info(
                    "✅ Published DeliveryCompletedEvent for delivery {}, shipper will receive {} (85% of {}), restaurant will receive {}",
                    delivery.getId(), shipperEarnings, delivery.getShippingFee(), restaurantEarnings);

        } catch (Exception e) {
            log.error("💥 Failed to publish DeliveryCompletedEvent for delivery {}: {}",
                    delivery.getId(), e.getMessage(), e);
            throw new IllegalStateException("Failed to store DeliveryCompletedEvent", e);
        }
    }

    private boolean positive(Long value) {
        return value != null && value > 0;
    }

    private void validateCreateDeliveryEvent(OrderCreatedEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("OrderCreatedEvent is required");
        }
        if (event.getEventId() == null) {
            throw new IllegalArgumentException("Create-delivery eventId is required");
        }
        if (!positive(event.getOrderId())) {
            throw new IllegalArgumentException("Create-delivery orderId must be positive");
        }
        if (!positive(event.getUserId())) {
            throw new IllegalArgumentException("Create-delivery userId must be positive");
        }
        if (!positive(event.getRestaurantId())) {
            throw new IllegalArgumentException("Create-delivery restaurantId must be positive");
        }
        if (!positive(event.getCreatorId())) {
            throw new IllegalArgumentException("Create-delivery creatorId must be positive");
        }
    }

    private void validateOrderCancelledEvent(OrderCancelledEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("OrderCancelledEvent is required");
        }
        if (!positive(event.getOrderId())) {
            throw new IllegalArgumentException("OrderCancelledEvent orderId must be positive");
        }
    }

    private void validateShipperNotFoundEvent(ShipperNotFoundEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("ShipperNotFoundEvent is required");
        }
        if (!positive(event.getDeliveryId())) {
            throw new IllegalArgumentException("ShipperNotFoundEvent deliveryId must be positive");
        }
        if (!positive(event.getOrderId())) {
            throw new IllegalArgumentException("ShipperNotFoundEvent orderId must be positive");
        }
    }

    private boolean sameOfferDeadline(LocalDateTime first, LocalDateTime second) {
        if (first == null || second == null) {
            return first == second;
        }
        // PostgreSQL timestamps can round or truncate sub-millisecond precision.
        return Math.abs(java.time.Duration.between(first, second).toNanos()) <= 1_000_000L;
    }

    @Override
    @Transactional
    public void cancelDeliveryFromOrderCancelledEvent(OrderCancelledEvent event) {
        validateOrderCancelledEvent(event);
        try {
            log.info("🚫 Processing order cancellation for orderId: {}", event.getOrderId());

            // Tìm delivery record theo orderId
            Delivery delivery = deliveryRepository.findByOrderIdForUpdate(event.getOrderId())
                    .orElse(null);

            if (delivery == null) {
                log.warn("⚠️ No delivery found for cancelled order: {}", event.getOrderId());
                return;
            }

            log.info("📦 Found delivery {} for cancelled order: {}, current status: {}",
                    delivery.getId(), event.getOrderId(), delivery.getStatus());

            if (delivery.getStatus() == DeliveryStatus.CANCELLED) {
                log.info("Cancellation already applied for delivery {}, skipping duplicate", delivery.getId());
                return;
            }

            // Cancel is allowed until pickup. Include every matching state so Saga
            // compensation cannot leave an orphan delivery behind.
            if (delivery.getStatus() == DeliveryStatus.PENDING ||
                    delivery.getStatus() == DeliveryStatus.FINDING_SHIPPER ||
                    delivery.getStatus() == DeliveryStatus.WAIT_SHIPPER_CONFIRM ||
                    delivery.getStatus() == DeliveryStatus.SHIPPER_NOT_FOUND ||
                    delivery.getStatus() == DeliveryStatus.ASSIGNED) {

                // Cập nhật trạng thái delivery thành CANCELLED
                delivery.setStatus(DeliveryStatus.CANCELLED);
                delivery.setOfferedShipperId(null);
                delivery.setOfferExpiresAt(null);
                delivery.setRejectReason("Order cancelled by user/admin - orderId: " + event.getOrderId());
                delivery.setUpdatedAt(LocalDateTime.now());

                deliveryRepository.save(delivery);

                // ✅ Publish event đánh dấu shipper rảnh (thay thế REST call)
                if (delivery.getShipperId() != null) {
                    deliveryEventPublisher.publishShipperStatusChange(
                            delivery.getShipperId(), "AVAILABLE", delivery.getId(), delivery.getOrderId());
                }

                log.info("✅ Successfully cancelled delivery {} for order: {}",
                        delivery.getId(), event.getOrderId());

            } else {
                // A correlated Saga command must receive an explicit failure. Silent
                // success would let Order/Saga converge to CANCELLED while Delivery
                // remains PICKED_UP, DELIVERING or DELIVERED.
                throw new InvalidStatusException(
                        "Cannot cancel delivery " + delivery.getId() + " in status " + delivery.getStatus());
            }

        } catch (Exception e) {
            log.error("💥 Error processing order cancellation for orderId: {}",
                    event.getOrderId(), e);
            throw new IllegalStateException("Failed to cancel delivery for order " + event.getOrderId(), e);
        }
    }

    /**
     * ✅ Cập nhật delivery status khi không tìm được shipper
     */
    @Override
    @Transactional
    public void updateDeliveryStatusFromShipperNotFoundEvent(ShipperNotFoundEvent event) {
        validateShipperNotFoundEvent(event);
        try {
            log.info("🔄 Processing ShipperNotFoundEvent for delivery: {}, order: {}",
                    event.getDeliveryId(), event.getOrderId());

            // Tìm delivery theo deliveryId
            Delivery delivery = deliveryRepository.findByIdForUpdate(event.getDeliveryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Delivery not found with id: " + event.getDeliveryId()));

            // Validate order ID match
            if (!delivery.getOrderId().equals(event.getOrderId())) {
                throw new InvalidStatusException("ShipperNotFound orderId does not match delivery");
            }

            if (delivery.getStatus() == DeliveryStatus.SHIPPER_NOT_FOUND) {
                log.info("Shipper-not-found already applied for delivery {}, skipping duplicate", delivery.getId());
                return;
            }

            // Chỉ cập nhật nếu delivery đang ở trạng thái FINDING_SHIPPER
            if (delivery.getStatus() != DeliveryStatus.FINDING_SHIPPER) {
                if (delivery.getStatus() == DeliveryStatus.ASSIGNED
                        || delivery.getStatus() == DeliveryStatus.PICKED_UP
                        || delivery.getStatus() == DeliveryStatus.DELIVERING
                        || delivery.getStatus() == DeliveryStatus.DELIVERED
                        || delivery.getStatus() == DeliveryStatus.CANCELLED) {
                    log.info("Ignoring stale shipper-not-found for delivery {} already in {}",
                            delivery.getId(), delivery.getStatus());
                    return;
                }
                throw new InvalidStatusException("Contradictory shipper-not-found event in status "
                        + delivery.getStatus());
            }

            // Cập nhật status thành SHIPPER_NOT_FOUND
            DeliveryStatus previousStatus = delivery.getStatus();
            delivery.setStatus(DeliveryStatus.SHIPPER_NOT_FOUND);
            delivery.setUpdatedAt(LocalDateTime.now());

            deliveryRepository.save(delivery);

            // Notification consumes the canonical delivery.status-updated topic.
            // Persist this event in the same transaction as the terminal status so
            // the customer is notified without introducing a second notification path.
            deliveryEventPublisher.publishDeliveryStatusUpdated(
                    delivery.getId(), delivery.getOrderId(), delivery.getCreatorId(), delivery.getShipperId(),
                    DeliveryStatus.SHIPPER_NOT_FOUND.name(), previousStatus.name());

            log.info("✅ Updated delivery {} status from {} to SHIPPER_NOT_FOUND after {} retry attempts",
                    delivery.getId(), previousStatus, event.getRetryAttempts());

        } catch (Exception e) {
            log.error("💥 Error updating delivery status from ShipperNotFoundEvent for delivery: {}: {}",
                    event.getDeliveryId(), e.getMessage(), e);
            throw new IllegalStateException("Failed to apply shipper-not-found event", e);
        }
    }

}
