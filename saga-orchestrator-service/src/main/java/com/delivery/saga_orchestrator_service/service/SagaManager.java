package com.delivery.saga_orchestrator_service.service;

import com.delivery.saga_orchestrator_service.entity.SagaInstance;
import com.delivery.saga_orchestrator_service.entity.SagaInstance.SagaStatus;
import com.delivery.saga_orchestrator_service.entity.SagaStep;
import com.delivery.saga_orchestrator_service.entity.SagaEarlyEvent;
import com.delivery.saga_orchestrator_service.repository.SagaInstanceRepository;
import com.delivery.saga_orchestrator_service.repository.SagaInboundReceiptRepository;
import com.delivery.saga_orchestrator_service.repository.SagaEarlyEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.UUID;

/**
 * ✅ Active Saga Manager — "Nhạc trưởng" phát lệnh điều phối luồng đặt hàng
 *
 * Flow:
 * 1. order.created → SAGA → [saga.command.create-delivery] → Delivery
 * 2. delivery.created.result → SAGA → [saga.command.find-shipper] → Match
 * 3. shipper.found → SAGA → [saga.command.cache-shipper-found] → Delivery
 *                          → [saga.command.update-order-status] → Order
 * 4. shipper.not-found → SAGA → [saga.command.update-order-status](SHIPPER_NOT_FOUND) → Order
 *                              → [saga.command.mark-shipper-not-found] → Delivery
 * 5. delivery.shipper-accepted → SAGA → [saga.command.update-order-status] → Order
 * 6. delivery.status-updated → SAGA → [saga.command.update-order-status] → Order
 */
@Slf4j
@Service
public class SagaManager {

    private final SagaInstanceRepository sagaInstanceRepository;
    private final SagaOutboxService outboxService;
    private final SagaInboundReceiptRepository inboundReceiptRepository;
    private final SagaEarlyEventRepository earlyEventRepository;
    private final ObjectMapper objectMapper;

    // ======== Saga Command Topics ========
    public static final String CMD_CREATE_DELIVERY = "saga.command.create-delivery";
    public static final String CMD_CANCEL_DELIVERY = "saga.command.cancel-delivery";
    public static final String CMD_FIND_SHIPPER = "saga.command.find-shipper";
    public static final String CMD_CACHE_SHIPPER_FOUND = "saga.command.cache-shipper-found";
    public static final String CMD_EXPIRE_SHIPPER_OFFER = "saga.command.expire-shipper-offer";
    public static final String CMD_MARK_SHIPPER_NOT_FOUND = "saga.command.mark-shipper-not-found";
    public static final String CMD_STOP_MATCHING = "saga.command.stop-matching";
    public static final String CMD_UPDATE_ORDER_STATUS = "saga.command.update-order-status";

    @Value("${app.kafka.topics.create-delivery:saga.command.create-delivery}")
    private String createDeliveryTopic = CMD_CREATE_DELIVERY;
    @Value("${app.kafka.topics.cancel-delivery:saga.command.cancel-delivery}")
    private String cancelDeliveryTopic = CMD_CANCEL_DELIVERY;
    @Value("${app.kafka.topics.find-shipper:saga.command.find-shipper}")
    private String findShipperTopic = CMD_FIND_SHIPPER;
    @Value("${app.kafka.topics.cache-shipper-found:saga.command.cache-shipper-found}")
    private String cacheShipperFoundTopic = CMD_CACHE_SHIPPER_FOUND;
    @Value("${app.kafka.topics.expire-shipper-offer:saga.command.expire-shipper-offer}")
    private String expireShipperOfferTopic = CMD_EXPIRE_SHIPPER_OFFER;
    @Value("${app.kafka.topics.mark-shipper-not-found:saga.command.mark-shipper-not-found}")
    private String markShipperNotFoundTopic = CMD_MARK_SHIPPER_NOT_FOUND;
    @Value("${app.kafka.topics.stop-matching:saga.command.stop-matching}")
    private String stopMatchingTopic = CMD_STOP_MATCHING;
    @Value("${app.kafka.topics.update-order-status:saga.command.update-order-status}")
    private String updateOrderStatusTopic = CMD_UPDATE_ORDER_STATUS;
    @Value("${matching.initial.max-retry-attempts:10}")
    private int initialMatchMaxRetryAttempts = 10;
    @Value("${matching.initial.delay-seconds:30}")
    private int initialMatchDelaySeconds = 30;
    @Value("${matching.initial.max-delay-seconds:300}")
    private int initialMatchMaxDelaySeconds = 300;
    @Value("${matching.initial.backoff-multiplier:1.5}")
    private double initialMatchBackoffMultiplier = 1.5;
    @Value("${app.saga.timeout.finding-shipper-minutes:5}")
    private int findingShipperTimeoutMinutes = 5;
    @Value("${matching.batch.client-capability-enabled:false}")
    private boolean batchClientCapabilityEnabled;
    @Value("${spring.datasource.url:}")
    private String dataSourceUrl;

    @Autowired
    public SagaManager(SagaInstanceRepository sagaInstanceRepository,
                       SagaOutboxService outboxService,
                       SagaInboundReceiptRepository inboundReceiptRepository,
                       SagaEarlyEventRepository earlyEventRepository) {
        this.sagaInstanceRepository = sagaInstanceRepository;
        this.outboxService = outboxService;
        this.inboundReceiptRepository = inboundReceiptRepository;
        this.earlyEventRepository = earlyEventRepository;
        this.objectMapper = new ObjectMapper();
    }

    /** Compatibility constructor for tests that exercise the durable inbox but not early-event staging. */
    public SagaManager(SagaInstanceRepository sagaInstanceRepository,
                       SagaOutboxService outboxService,
                       SagaInboundReceiptRepository inboundReceiptRepository) {
        this(sagaInstanceRepository, outboxService, inboundReceiptRepository, null);
    }

    /** Compatibility constructor for focused tests that do not exercise the inbox boundary. */
    public SagaManager(SagaInstanceRepository sagaInstanceRepository, SagaOutboxService outboxService) {
        this(sagaInstanceRepository, outboxService, null, null);
    }

    // ==================== EVENT HANDLERS ====================

    /**
     * Step 1: order.created → Tạo saga + phát lệnh tạo delivery
     */
    @Transactional
    public void handleOrderCreated(Long orderId, String rawEvent) {
        if (!claimInbound("order.created", orderId, rawEvent)) return;
        // Idempotent check
        SagaInstance existing = sagaInstanceRepository.findByOrderIdForUpdate(orderId).orElse(null);
        if (existing != null) {
            if (sameJson(existing.getPayload(), rawEvent)) {
                log.info("[Saga] Exact order.created replay for orderId={}, skipping", orderId);
                return;
            }
            throw new IllegalStateException("order.created conflicts with existing Saga payload for orderId="
                    + orderId);
        }

        SagaInstance saga = new SagaInstance();
        saga.setSagaType("ORDER_CREATION");
        saga.setOrderId(orderId);
        saga.setStatus(SagaStatus.STARTED);
        saga.setPayload(rawEvent);
        saga.addStep("ORDER_CREATED", "order.created", rawEvent);
        sagaInstanceRepository.save(saga);

        log.info("🆕 [Saga] Created saga for orderId={}, id={}", orderId, saga.getId());

        // A cancellation/restaurant decision may have arrived on another topic
        // while order.created was delayed. Promote those durable facts before
        // dispatching create-delivery so cancellation cannot create an orphan.
        drainEarlyEventsForSaga(saga);
        if (saga.getStatus() != SagaStatus.STARTED) {
            log.info("[Saga] orderId={} has early terminal/advance fact; skipping create-delivery in status={}",
                    orderId, saga.getStatus());
            return;
        }

        // ✅ PHÁT LỆNH: Tạo delivery
        sendCommand(CMD_CREATE_DELIVERY, orderId.toString(), rawEvent);
        log.info("📤 [Saga] Sent command: {} for orderId={}", CMD_CREATE_DELIVERY, orderId);
    }

    /**
     * Step 2: delivery.created.result → Delivery đã tạo.
     * ✅ GATE: KHÔNG tìm shipper ngay. Chỉ tìm shipper SAU KHI nhà hàng confirm đơn
     * (restaurant.order-confirmed). Nếu nhà hàng đã confirm trước đó (race) thì tìm luôn.
     */
    @Transactional
    public void handleDeliveryCreated(Long orderId, Long deliveryId, String rawEvent) {
        if (!claimInbound("delivery.created.result", orderId, rawEvent)) return;
        SagaInstance saga = findSagaByOrderId(orderId);

        // An exact replay is harmless, but a second delivery identity for the
        // same order is a contradictory event and must not be ACK-discarded.
        if (saga.getStatus() != SagaStatus.STARTED) {
            if (deliveryId != null && deliveryId.equals(saga.getDeliveryId())
                    && hasStep(saga, "DELIVERY_CREATED")) {
                log.info("[Saga] Exact delivery-created replay for orderId={}, deliveryId={}, skipping",
                        orderId, deliveryId);
                return;
            }
            // A cancellation or timeout may overtake the result of an in-flight
            // create-delivery command on another topic. The late result is a
            // valid consequence of work already dispatched, not a contradictory
            // terminal transition. Record its single identity and re-issue the
            // cancellation so a Delivery cannot remain orphaned after either a
            // customer cancellation or a failed STARTED timeout.
            if ((saga.getStatus() == SagaStatus.CANCELLED || saga.getStatus() == SagaStatus.FAILED)
                    && saga.getDeliveryId() == null
                    && !hasStep(saga, "DELIVERY_CREATED")) {
                if (deliveryId == null || deliveryId <= 0) {
                    throw new IllegalArgumentException(
                            "deliveryId must be positive for cancelled orderId=" + orderId);
                }
                saga.setDeliveryId(deliveryId);
                saga.addStep("DELIVERY_CREATED", "delivery.created.result", rawEvent);
                sagaInstanceRepository.save(saga);
                sendCommand(CMD_CANCEL_DELIVERY, orderId.toString(), rawEvent);
                log.info("[Saga] Late delivery-created result for terminal orderId={}, "
                        + "deliveryId={}; cancellation re-issued after {}", orderId, deliveryId,
                        saga.getStatus());
                return;
            }
            throw new IllegalStateException("Contradictory delivery-created event for order "
                    + orderId + ": existing delivery=" + saga.getDeliveryId()
                    + ", received delivery=" + deliveryId + ", saga status=" + saga.getStatus());
        }

        saga.setDeliveryId(deliveryId);
        saga.setStatus(SagaStatus.DELIVERY_CREATED);
        saga.addStep("DELIVERY_CREATED", "delivery.created.result", rawEvent);
        sagaInstanceRepository.save(saga);

        // ✅ Nếu nhà hàng đã confirm trước khi delivery tạo xong (race) → tìm shipper luôn.
        if (hasStep(saga, "RESTAURANT_CONFIRMED")) {
            log.info("🍽️ [Saga] Nhà hàng đã confirm trước; tìm shipper ngay cho orderId={}", orderId);
            triggerFindShipper(saga, orderId, deliveryId, rawEvent);
        } else {
            log.info("⏸️ [Saga] Delivery tạo xong, CHỜ nhà hàng confirm mới tìm shipper. orderId={}", orderId);
        }
    }

    /**
     * ✅ restaurant.order-confirmed → Mở cổng tìm shipper.
     * Xử lý cả 2 thứ tự: confirm đến trước hay sau delivery.created.result.
     */
    @Transactional
    public void handleRestaurantConfirmed(Long orderId, String rawEvent) {
        SagaInstance saga = sagaInstanceRepository.findByOrderIdForUpdate(orderId).orElse(null);
        if (saga == null) {
            stageEarlyEvent("restaurant.order-confirmed", orderId, rawEvent);
            return;
        }
        if (!claimInbound("restaurant.order-confirmed", orderId, rawEvent)) return;
        applyRestaurantConfirmedLocked(saga, rawEvent);
    }

    private void applyRestaurantConfirmedLocked(SagaInstance saga, String rawEvent) {
        Long orderId = saga.getOrderId();
        // Chỉ chấp nhận confirm khi còn ở giai đoạn đầu (chưa tìm shipper / chưa kết thúc).
        if (saga.getStatus() != SagaStatus.STARTED && saga.getStatus() != SagaStatus.DELIVERY_CREATED) {
            log.warn("⚠️ [Saga] handleRestaurantConfirmed - orderId={} đang ở {}, bỏ qua", orderId, saga.getStatus());
            return;
        }
        if (hasStep(saga, "RESTAURANT_CONFIRMED")) {
            log.warn("⚠️ [Saga] Nhà hàng đã confirm trước đó cho orderId={}, bỏ qua (idempotent)", orderId);
            return;
        }

        saga.addStep("RESTAURANT_CONFIRMED", "restaurant.order-confirmed", rawEvent);
        sagaInstanceRepository.save(saga);

        if (saga.getStatus() == SagaStatus.DELIVERY_CREATED && saga.getDeliveryId() != null) {
            // Delivery đã sẵn sàng → tìm shipper ngay, dùng lại payload delivery.created.result.
            String deliveryEvent = getStepEventData(saga, "DELIVERY_CREATED");
            if (deliveryEvent == null) {
                throw new IllegalStateException(
                        "Saga DELIVERY_CREATED is missing its canonical delivery.created.result payload");
            }
            log.info("🍽️ [Saga] Nhà hàng confirm orderId={} → tìm shipper", orderId);
            triggerFindShipper(saga, orderId, saga.getDeliveryId(), deliveryEvent);
        } else {
            // Delivery chưa tạo xong → chỉ ghi nhận; handleDeliveryCreated sẽ tự tìm khi tới.
            log.info("🍽️ [Saga] Nhà hàng confirm orderId={} nhưng delivery chưa sẵn sàng, sẽ tìm shipper sau", orderId);
        }
    }

    /**
     * Phát lệnh tìm shipper (kèm cấu hình retry) + chuyển saga sang FINDING_SHIPPER.
     * Dùng chung cho: delivery-created (đã confirm) và restaurant-confirmed (delivery đã tạo).
     */
    private void triggerFindShipper(SagaInstance saga, Long orderId, Long deliveryId, String deliveryResultEvent) {
        String modifiedEvent;
        try {
            ObjectNode payloadNode = buildFindShipperPayload(saga, deliveryResultEvent);
            payloadNode.put("maxRetryAttempts", initialMatchMaxRetryAttempts);
            payloadNode.put("initialDelaySeconds", initialMatchDelaySeconds);
            payloadNode.put("maxDelaySeconds", initialMatchMaxDelaySeconds);
            payloadNode.put("backoffMultiplier", initialMatchBackoffMultiplier);
            payloadNode.put("matchingDeadlineAt", LocalDateTime.now()
                    .plusMinutes(Math.max(1, findingShipperTimeoutMinutes)).toString());

            modifiedEvent = dispatchFindShipperCommand(saga, orderId, payloadNode);
            sendOrderStatusCommand(orderId, "FINDING_SHIPPER", modifiedEvent);
            log.info("📤 [Saga] Sent command: {} for orderId={}, deliveryId={} with retry settings", CMD_FIND_SHIPPER, orderId, deliveryId);
        } catch (Exception e) {
            if (e instanceof SagaCommandPublishException publishException) {
                throw publishException;
            }
            throw new IllegalStateException("Cannot build canonical find-shipper command", e);
        }

        saga.setStatus(SagaStatus.FINDING_SHIPPER);
        sagaInstanceRepository.save(saga);
    }

    /**
     * Step 3a: shipper.found → request Delivery persist the offer. Order must
     * remain FINDING_SHIPPER until Delivery confirms its own transaction.
     */
    @Transactional
    public void handleShipperFound(Long orderId, Long deliveryId, String rawEvent) {
        if (!claimInbound("shipper.found", orderId, rawEvent)) return;
        SagaInstance saga = findSagaByOrderId(orderId);
        requireDeliveryIdentity(saga, deliveryId, orderId);
        if (!isCurrentMatchingResult(saga, rawEvent)) {
            log.info("[Saga] Ignoring stale shipper.found generation for orderId={}", orderId);
            return;
        }

        // Idempotency check: chỉ xử lý khi đang FINDING_SHIPPER
        if (saga.getStatus() != SagaStatus.FINDING_SHIPPER) {
            log.warn("⚠️ [Saga] handleShipperFound - Saga cho orderId={} đang ở {}, bỏ qua event", orderId, saga.getStatus());
            return;
        }

        saga.setStatus(SagaStatus.OFFER_PERSISTING);
        saga.addStep("SHIPPER_FOUND", "shipper.found", rawEvent);
        UUID cacheCommandId = sendCommand(CMD_CACHE_SHIPPER_FOUND, orderId.toString(), rawEvent);
        ObjectNode requested = objectMapper.createObjectNode();
        requested.put("cacheCommandEventId", cacheCommandId.toString());
        try {
            requested.put("matchingSessionId", objectMapper.readTree(rawEvent).path("matchingSessionId").asText());
        } catch (Exception invalid) {
            throw new IllegalArgumentException("Invalid shipper.found payload", invalid);
        }
        saga.addStep("OFFER_PERSIST_REQUESTED", CMD_CACHE_SHIPPER_FOUND, requested.toString());
        sagaInstanceRepository.save(saga);

        log.info("📤 [Saga] Sent cache-shipper command for orderId={}; awaiting Delivery confirmation", orderId);
    }

    /** Delivery owns the offer. Only this committed confirmation may expose WAIT to Order. */
    @Transactional
    public void handleOfferPersisted(Long orderId, Long deliveryId, String rawEvent) {
        if (!claimInbound("delivery.offer-persisted", orderId, rawEvent)) return;
        SagaInstance saga = findSagaByOrderId(orderId);
        requireDeliveryIdentity(saga, deliveryId, orderId);
        JsonNode event;
        try {
            event = objectMapper.readTree(rawEvent);
            UUID.fromString(event.path("sourceCommandEventId").asText());
            UUID.fromString(event.path("matchingSessionId").asText());
            if (event.path("offeredShipperId").asLong() <= 0 || !event.hasNonNull("offerExpiresAt")) {
                throw new IllegalArgumentException("offer-persisted identity is incomplete");
            }
        } catch (IllegalArgumentException invalid) {
            throw invalid;
        } catch (Exception invalid) {
            throw new IllegalArgumentException("Invalid delivery.offer-persisted payload", invalid);
        }
        if (saga.getStatus() == SagaStatus.SHIPPER_ASSIGNED
                || saga.getStatus() == SagaStatus.CANCELLED || saga.getStatus() == SagaStatus.FAILED) {
            log.info("[Saga] Offer confirmation arrived after stronger state {} for orderId={}",
                    saga.getStatus(), orderId);
            return;
        }
        if (saga.getStatus() != SagaStatus.OFFER_PERSISTING
                || !isCurrentMatchingResult(saga, rawEvent)
                || !isExpectedOfferPersistenceCommand(saga, event.path("sourceCommandEventId").asText())) {
            log.info("[Saga] Ignoring stale/unexpected offer confirmation for orderId={}", orderId);
            return;
        }
        saga.setStatus(SagaStatus.SHIPPER_FOUND);
        saga.addStep("OFFER_PERSISTED", "delivery.offer-persisted", rawEvent);
        sagaInstanceRepository.save(saga);
        sendOrderStatusCommand(saga, "WAIT_SHIPPER_CONFIRM", rawEvent);
    }

    /**
     * Step 3b: shipper.not-found → Compensation: cancel delivery + update order FAILED
     */
    @Transactional
    public void handleShipperNotFound(Long orderId, Long deliveryId, String rawEvent) {
        if (!claimInbound("shipper.not-found", orderId, rawEvent)) return;
        SagaInstance saga = findSagaByOrderId(orderId);
        requireDeliveryIdentity(saga, deliveryId, orderId);
        if (!isCurrentMatchingResult(saga, rawEvent)) {
            log.info("[Saga] Ignoring stale shipper.not-found generation for orderId={}", orderId);
            return;
        }

        // Idempotency check
        if (saga.getStatus() != SagaStatus.FINDING_SHIPPER) {
            log.warn("⚠️ [Saga] handleShipperNotFound - Saga cho orderId={} đang ở {}, bỏ qua event", orderId, saga.getStatus());
            return;
        }

        saga.setStatus(SagaStatus.FAILED);
        saga.setCompletedAt(LocalDateTime.now());
        saga.addStep("SHIPPER_NOT_FOUND", "shipper.not-found", rawEvent);
        sagaInstanceRepository.save(saga);

        // This is a terminal matching outcome, not an order cancellation.
        // Keeping the commands distinct prevents Delivery=CANCELLED while
        // Order=SHIPPER_NOT_FOUND.
        sendCommand(CMD_MARK_SHIPPER_NOT_FOUND, orderId.toString(), rawEvent);

        // ✅ COMPENSATION: Cập nhật order status → FAILED
        sendOrderStatusCommand(orderId, "SHIPPER_NOT_FOUND", rawEvent);

        log.warn("🚨 [Saga] COMPENSATION — shipper not found, orderId={}", orderId);
    }

    /**
     * Step 4: delivery.shipper-accepted → Cập nhật order status
     */
    @Transactional
    public void handleShipperAccepted(Long orderId, Long deliveryId, Long shipperId, String rawEvent) {
        if (!claimInbound("delivery.shipper-accepted", orderId, rawEvent)) return;
        SagaInstance saga = findSagaByOrderId(orderId);
        requireDeliveryIdentity(saga, deliveryId, orderId);

        if (shipperId == null || shipperId <= 0) {
            throw new IllegalArgumentException("shipperId must be positive");
        }

        if (saga.getStatus() == SagaStatus.SHIPPER_ASSIGNED
                || saga.getStatus() == SagaStatus.PICKING_UP
                || saga.getStatus() == SagaStatus.DELIVERING
                || saga.getStatus() == SagaStatus.COMPLETED) {
            if (shipperId.equals(saga.getShipperId()) && hasStep(saga, "SHIPPER_ASSIGNED")) {
                log.info("[Saga] Exact shipper-accepted replay for orderId={}, shipperId={}, skipping",
                        orderId, shipperId);
                return;
            }
            throw new IllegalStateException("Shipper acceptance conflicts with saga assignment for orderId="
                    + orderId);
        }

        // Rejection/cancel-assignment and acceptance are published on different
        // Kafka topics. A delayed replay of the old acceptance must not resurrect
        // a shipper that this Saga has already excluded while rematching. Offer
        // timeout is intentionally different: an acceptance committed in Delivery
        // just before the deadline may legitimately overtake the timeout event.
        if ((saga.getStatus() == SagaStatus.FINDING_SHIPPER
                || saga.getStatus() == SagaStatus.SHIPPER_FOUND)
                && hasRejectedShipper(saga, shipperId)) {
            log.info("[Saga] Ignoring stale acceptance from rejected shipper {} for orderId={}",
                    shipperId, orderId);
            return;
        }

        // Idempotency check
        if (saga.getStatus() != SagaStatus.SHIPPER_FOUND && saga.getStatus() != SagaStatus.FINDING_SHIPPER) {
            log.warn("⚠️ [Saga] handleShipperAccepted - Saga cho orderId={} đang ở {}, bỏ qua event", orderId, saga.getStatus());
            return;
        }

        saga.setStatus(SagaStatus.SHIPPER_ASSIGNED);
        saga.setShipperId(shipperId);
        saga.addStep("SHIPPER_ASSIGNED", "delivery.shipper-accepted", rawEvent);
        sagaInstanceRepository.save(saga);

        // ✅ PHÁT LỆNH: Cập nhật order status
        sendOrderStatusCommand(orderId, "SHIPPER_ASSIGNED", rawEvent);

        log.info("📤 [Saga] Shipper {} assigned, sent update-order for orderId={}", shipperId, orderId);
    }

    /**
     * Step 4b: delivery.shipper-rejected → Re-trigger tìm shipper mới (loại trừ shipper đã reject)
     */
    @Transactional
    public void handleShipperRejected(Long orderId, Long deliveryId, Long rejectedShipperId, String rawEvent) {
        if (!claimInbound("delivery.shipper-rejected", orderId, rawEvent)) return;
        SagaInstance saga = findSagaByOrderId(orderId);
        requireDeliveryIdentity(saga, deliveryId, orderId);

        // Pre-accept rejection arrives in SHIPPER_FOUND/FINDING_SHIPPER. A shipper
        // cancel-assignment after accept arrives in SHIPPER_ASSIGNED and must also
        // rematch rather than being silently discarded.
        if (saga.getStatus() != SagaStatus.SHIPPER_FOUND
                && saga.getStatus() != SagaStatus.FINDING_SHIPPER
                && saga.getStatus() != SagaStatus.SHIPPER_ASSIGNED) {
            log.warn("⚠️ [Saga] handleShipperRejected - Saga cho orderId={} đang ở {}, bỏ qua event", orderId, saga.getStatus());
            return;
        }

        if (saga.getStatus() == SagaStatus.SHIPPER_ASSIGNED
                && (saga.getShipperId() == null || !saga.getShipperId().equals(rejectedShipperId))) {
            throw new IllegalStateException("Assigned shipper does not match rejected shipper for orderId=" + orderId);
        }

        if (rejectedShipperId != null && hasRejectedShipper(saga, rejectedShipperId)) {
            log.info("[Saga] Duplicate rejection from shipper {} for orderId={}, skipping",
                    rejectedShipperId, orderId);
            return;
        }

        // Đếm số lần shipper đã reject cho đơn này
        long rejectCount = saga.getSteps().stream()
                .filter(s -> s.getStepName().startsWith("SHIPPER_REJECTED"))
                .count() + 1;

        // Giới hạn tối đa 5 lần re-assign
        if (rejectCount > 5) {
            log.warn("🚨 [Saga] Too many shipper rejections ({}) for orderId={}, failing saga", rejectCount, orderId);
            saga.setStatus(SagaStatus.FAILED);
            saga.setCompletedAt(LocalDateTime.now());
            saga.addStep("SHIPPER_REJECTED_LIMIT", "delivery.shipper-rejected", rawEvent);
            sagaInstanceRepository.save(saga);

            sendCommand(CMD_MARK_SHIPPER_NOT_FOUND, orderId.toString(), rawEvent);
            sendOrderStatusCommand(orderId, "SHIPPER_NOT_FOUND", rawEvent);
            return;
        }

        saga.setStatus(SagaStatus.FINDING_SHIPPER);
        saga.setShipperId(null);
        saga.addStep("SHIPPER_REJECTED_" + rejectCount, "delivery.shipper-rejected", rawEvent);
        sagaInstanceRepository.save(saga);

        log.info("🔄 [Saga] Shipper {} rejected orderId={} (attempt {}), re-triggering find-shipper",
                rejectedShipperId, orderId, rejectCount);

        // ✅ Collect all rejected shipper IDs from saga steps
        java.util.List<Long> excludedShipperIds = new java.util.ArrayList<>();
        if (rejectedShipperId != null) {
            excludedShipperIds.add(rejectedShipperId);
        }
        // Also extract from previous rejection steps
        for (SagaStep step : saga.getSteps()) {
            if (step.getStepName().startsWith("SHIPPER_REJECTED") && step.getEventData() != null) {
                try {
                    com.fasterxml.jackson.databind.JsonNode stepData = objectMapper.readTree(step.getEventData());
                    if (stepData.has("rejectedShipperId")) {
                        Long prevRejected = stepData.get("rejectedShipperId").asLong();
                        if (!excludedShipperIds.contains(prevRejected)) {
                            excludedShipperIds.add(prevRejected);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        try {
            // ✅ Enrich payload with excludedShipperIds + retry settings for match-service
            ObjectNode payloadNode = buildFindShipperPayload(saga, rawEvent);
            payloadNode.put("maxRetryAttempts", 5);
            payloadNode.put("initialDelaySeconds", 15);
            payloadNode.put("maxDelaySeconds", 120);
            payloadNode.put("backoffMultiplier", 1.5);

            // Add excluded shipper IDs
            com.fasterxml.jackson.databind.node.ArrayNode excludedArray = objectMapper.createArrayNode();
            for (Long id : excludedShipperIds) {
                excludedArray.add(id);
            }
            payloadNode.set("excludedShipperIds", excludedArray);

            String modifiedEvent = dispatchFindShipperCommand(saga, orderId, payloadNode);
            sagaInstanceRepository.save(saga);

            log.info("📤 [Saga] Re-sent {} for orderId={} with excludedShippers={}",
                    CMD_FIND_SHIPPER, orderId, excludedShipperIds);
        } catch (Exception e) {
            if (e instanceof SagaCommandPublishException publishException) {
                throw publishException;
            }
            throw new IllegalStateException("Cannot build canonical rematch command for orderId=" + orderId, e);
        }

        // ✅ Update order status to let frontend know we're re-searching
        sendOrderStatusCommand(orderId, "FINDING_SHIPPER", rawEvent);
    }

    /**
     * Applies a scheduler observation only when the Saga has not changed since
     * it was selected. This is deliberately a first-class inbox command rather
     * than anonymous synthetic JSON: a repeated poll is an exact replay and a
     * stale poll cannot compensate a newer state.
     */
    @Transactional
    public void handleTimeout(SagaTimeoutCommand command) {
        if (command == null || command.orderId() == null || command.orderId() <= 0
                || command.eventId() == null || command.expectedStatus() == null) {
            throw new IllegalArgumentException("Timeout command identity, orderId and expected status are required");
        }
        SagaInstance saga = findSagaByOrderId(command.orderId());
        if (!matchesTimeoutObservation(saga, command)) {
            log.info("[Saga] Ignoring stale timeout for orderId={} expected={}/{} observed={}/{}",
                    command.orderId(), command.expectedStatus(), command.expectedVersion(),
                    saga.getStatus(), versionOf(saga));
            return;
        }

        // A shipper-offer candidate is intentionally queried from a very small
        // minimum age. Do not consume an inbox receipt until its exact offer
        // deadline has actually elapsed.
        if (command.expectedStatus() == SagaStatus.SHIPPER_FOUND) {
            if (!isShipperOfferTimeoutDue(saga)) {
                return;
            }
        } else if (LocalDateTime.now().isBefore(command.deadline())) {
            log.info("[Saga] Ignoring early timeout for orderId={} status={} deadline={}",
                    command.orderId(), command.expectedStatus(), command.deadlineAt());
            return;
        }

        String rawTimeoutEvent = command.toJson(objectMapper);
        if (!claimInbound("saga.timeout." + command.expectedStatus().name(),
                command.orderId(), rawTimeoutEvent)) {
            return;
        }

        if (command.expectedStatus() == SagaStatus.SHIPPER_FOUND) {
            handleShipperOfferTimeoutLocked(saga, rawTimeoutEvent);
            return;
        }
        handleStepFailedLocked("TIMEOUT_" + command.expectedStatus().name(), saga,
                command.reason(), rawTimeoutEvent);
    }

    /**
     * Compatibility entry point for focused callers. Production scheduling uses
     * {@link #handleTimeout(SagaTimeoutCommand)} so it carries a snapshot fence.
     */
    @Transactional
    public void handleShipperOfferTimeout(Long orderId) {
        SagaInstance saga = findSagaByOrderId(orderId);
        if (saga.getStatus() != SagaStatus.SHIPPER_FOUND) {
            return;
        }
        handleTimeout(SagaTimeoutCommand.forShipperOffer(saga, "Shipper offer timeout"));
    }

    /**
     * A shipper did not answer the single active offer. Re-run matching with the
     * timed-out shipper excluded; only compensate after the shared attempt limit.
     * The aggregate is already locked and the timeout has already been claimed.
     */
    private void handleShipperOfferTimeoutLocked(SagaInstance saga, String rawTimeoutEvent) {
        Long orderId = saga.getOrderId();

        SagaStep foundStep = null;
        for (int i = saga.getSteps().size() - 1; i >= 0; i--) {
            SagaStep step = saga.getSteps().get(i);
            if ("SHIPPER_FOUND".equals(step.getStepName())) {
                foundStep = step;
                break;
            }
        }
        if (foundStep == null || foundStep.getEventData() == null) {
            handleStepFailedLocked("SHIPPER_OFFER_TIMEOUT", saga,
                    "Missing shipper offer payload", rawTimeoutEvent);
            return;
        }

        long previousFailedOffers = saga.getSteps().stream()
                .filter(step -> step.getStepName().startsWith("SHIPPER_REJECTED")
                        || step.getStepName().startsWith("SHIPPER_OFFER_TIMEOUT"))
                .count();
        if (previousFailedOffers >= 5) {
            handleStepFailedLocked("SHIPPER_OFFER_TIMEOUT_LIMIT", saga,
                    "Shipper offer attempts exhausted", rawTimeoutEvent);
            return;
        }

        try {
            ObjectNode payload = buildFindShipperPayload(saga, foundStep.getEventData());
            JsonNode foundPayload = objectMapper.readTree(foundStep.getEventData());
            if (!foundPayload.has("availableShippers")
                    || !foundPayload.get("availableShippers").isArray()
                    || foundPayload.get("availableShippers").size() != 1
                    || !foundPayload.get("availableShippers").get(0).hasNonNull("shipperId")) {
                throw new IllegalStateException("Offer payload must contain exactly one shipper");
            }
            Long timedOutShipperId = foundPayload.get("availableShippers").get(0)
                    .get("shipperId").asLong();
            if (timedOutShipperId <= 0) {
                throw new IllegalStateException("Offer payload shipperId must be positive");
            }

            int offerTimeoutSeconds = foundPayload.hasNonNull("waitingTimeoutSeconds")
                    ? Math.max(1, Math.min(foundPayload.get("waitingTimeoutSeconds").asInt(), 180))
                    : 180;
            LocalDateTime offerFoundAt = foundPayload.hasNonNull("foundAt")
                    ? LocalDateTime.parse(foundPayload.get("foundAt").asText())
                    : foundStep.getExecutedAt();
            LocalDateTime offerExpiresAt = offerFoundAt.plusSeconds(offerTimeoutSeconds);
            if (offerExpiresAt.isAfter(LocalDateTime.now())) {
                log.debug("[Saga] Offer is still active for orderId={} until {}, skipping timeout poll",
                        orderId, offerExpiresAt);
                return;
            }

            java.util.LinkedHashSet<Long> excluded = new java.util.LinkedHashSet<>();
            for (SagaStep step : saga.getSteps()) {
                if (step.getEventData() == null) continue;
                try {
                    JsonNode stepData = objectMapper.readTree(step.getEventData());
                    if (stepData.hasNonNull("rejectedShipperId")) {
                        excluded.add(stepData.get("rejectedShipperId").asLong());
                    }
                } catch (Exception ignored) {
                    // A malformed historic step must not erase valid exclusions.
                }
            }
            if (timedOutShipperId != null) {
                excluded.add(timedOutShipperId);
                payload.put("rejectedShipperId", timedOutShipperId);
            }

            var excludedArray = objectMapper.createArrayNode();
            excluded.forEach(excludedArray::add);
            payload.set("excludedShipperIds", excludedArray);
            payload.put("maxRetryAttempts", 5);
            payload.put("initialDelaySeconds", 15);
            payload.put("maxDelaySeconds", 120);
            payload.put("backoffMultiplier", 1.5);

            String rematchEvent = objectMapper.writeValueAsString(payload);
            saga.setStatus(SagaStatus.FINDING_SHIPPER);
            saga.addStep("SHIPPER_OFFER_TIMEOUT_" + (previousFailedOffers + 1),
                    "shipper.offer-timeout", rematchEvent);

            ObjectNode expireCommand = objectMapper.createObjectNode();
            expireCommand.put("orderId", orderId);
            expireCommand.put("deliveryId", saga.getDeliveryId());
            expireCommand.put("timedOutShipperId", timedOutShipperId);
            expireCommand.put("expectedOfferExpiresAt", offerExpiresAt.toString());
            UUID matchingSessionId = currentMatchingSessionId(saga);
            if (matchingSessionId != null) {
                expireCommand.put("matchingSessionId", matchingSessionId.toString());
            }

            sendCommand(CMD_EXPIRE_SHIPPER_OFFER, orderId.toString(),
                    objectMapper.writeValueAsString(expireCommand));
            rematchEvent = dispatchFindShipperCommand(saga, orderId, payload);
            sagaInstanceRepository.save(saga);
            sendOrderStatusCommand(orderId, "FINDING_SHIPPER", rematchEvent);
            log.info("🔄 [Saga] Offer timed out for shipper {}, rematching orderId={} exclusions={}",
                    timedOutShipperId, orderId, excluded);
        } catch (Exception e) {
            if (e instanceof SagaCommandPublishException publishException) {
                throw publishException;
            }
            log.error("[Saga] Cannot build offer-timeout rematch command for orderId={}", orderId, e);
            handleStepFailedLocked("SHIPPER_OFFER_TIMEOUT", saga,
                    "Cannot build rematch command: " + e.getMessage(), rawTimeoutEvent);
        }
    }

    /**
     * Step 5: delivery.status-updated → Forward status đến order-service
     */
    @Transactional
    public void handleDeliveryStatusUpdated(Long orderId, Long deliveryId, String newStatus, String rawEvent) {
        if (!claimInbound("delivery.status-updated", orderId, rawEvent)) return;
        SagaInstance saga = findSagaByOrderId(orderId);
        requireDeliveryIdentity(saga, deliveryId, orderId);

        if ("SHIPPER_NOT_FOUND".equals(newStatus)) {
            handleDeliveryShipperNotFoundStatusEcho(saga, orderId, rawEvent);
            return;
        }

        SagaStatus targetStatus = switch (newStatus) {
            case "PICKED_UP" -> SagaStatus.PICKING_UP;
            case "DELIVERING" -> SagaStatus.DELIVERING;
            case "DELIVERED" -> SagaStatus.COMPLETED;
            case "CANCELLED" -> SagaStatus.CANCELLED;
            default -> throw new IllegalArgumentException("Unsupported delivery status: " + newStatus);
        };

        String stepName = "DELIVERY_" + newStatus;
        String appliedEvent = getStepEventData(saga, stepName);
        if (appliedEvent != null) {
            if (sameJson(appliedEvent, rawEvent)) {
                log.info("[Saga] Exact delivery status replay {} for orderId={}, skipping", newStatus, orderId);
                return;
            }
            throw new IllegalStateException("Conflicting delivery status replay " + newStatus
                    + " for orderId=" + orderId);
        }
        if (targetStatus == SagaStatus.CANCELLED && isCancellationConfirmation(saga)) {
            if (saga.getStatus() == SagaStatus.COMPENSATING) {
                saga.setStatus(SagaStatus.CANCELLED);
                saga.setCompletedAt(LocalDateTime.now());
            }
            saga.addStep(stepName, "delivery.status-updated", rawEvent);
            sagaInstanceRepository.save(saga);
            log.info("[Saga] Recorded delivery cancellation confirmation for terminal/compensating "
                    + "orderId={} status={}", orderId, saga.getStatus());
            return;
        }
        if (saga.getStatus() == SagaStatus.COMPLETED
                || saga.getStatus() == SagaStatus.CANCELLED
                || saga.getStatus() == SagaStatus.FAILED) {
            throw new IllegalStateException("Terminal saga " + saga.getStatus()
                    + " cannot apply delivery status " + newStatus + " for orderId=" + orderId);
        }
        requireDeliveryTransition(saga.getStatus(), targetStatus, orderId);
        saga.setStatus(targetStatus);
        if (targetStatus == SagaStatus.COMPLETED || targetStatus == SagaStatus.CANCELLED) {
            saga.setCompletedAt(LocalDateTime.now());
        }

        saga.addStep(stepName, "delivery.status-updated", rawEvent);
        sagaInstanceRepository.save(saga);

        // ✅ PHÁT LỆNH: Cập nhật order status
        sendOrderStatusCommand(orderId, newStatus, rawEvent);

        log.info("📤 [Saga] Delivery status={}, forwarded to order for orderId={}", newStatus, orderId);
    }

    private void handleDeliveryShipperNotFoundStatusEcho(SagaInstance saga, Long orderId, String rawEvent) {
        String stepName = "DELIVERY_SHIPPER_NOT_FOUND";
        String appliedEvent = getStepEventData(saga, stepName);
        if (appliedEvent != null) {
            if (sameJson(appliedEvent, rawEvent)) {
                log.info("[Saga] Exact delivery SHIPPER_NOT_FOUND replay for orderId={}, skipping", orderId);
                return;
            }
            throw new IllegalStateException("Conflicting delivery SHIPPER_NOT_FOUND replay for orderId=" + orderId);
        }

        if (saga.getStatus() != SagaStatus.FAILED || !hasStep(saga, "SHIPPER_NOT_FOUND")) {
            throw new IllegalStateException("Delivery SHIPPER_NOT_FOUND status must follow shipper.not-found "
                    + "for orderId=" + orderId);
        }

        saga.addStep(stepName, "delivery.status-updated", rawEvent);
        sagaInstanceRepository.save(saga);

        // Order was already converged by handleShipperNotFound. This downstream
        // Delivery event exists so Notification can inform the customer; Saga must
        // ACK it without issuing a duplicate update-order command.
        log.info("📥 [Saga] Recorded delivery SHIPPER_NOT_FOUND terminal echo for orderId={}", orderId);
    }

    /**
     * order.cancelled → Compensation: cancel delivery + stop matching
     */
    @Transactional
    public void handleOrderCancelled(Long orderId, String rawEvent) {
        SagaInstance saga = sagaInstanceRepository.findByOrderIdForUpdate(orderId).orElse(null);
        if (saga == null) {
            stageEarlyEvent("order.cancelled", orderId, rawEvent);
            return;
        }
        if (!claimInbound("order.cancelled", orderId, rawEvent)) return;
        applyOrderCancelledLocked(saga, rawEvent);
    }

    private void applyOrderCancelledLocked(SagaInstance saga, String rawEvent) {
        Long orderId = saga.getOrderId();
        if (saga.getStatus() == SagaStatus.CANCELLED
                || (saga.getStatus() == SagaStatus.COMPENSATING && hasStep(saga, "ORDER_CANCELLED"))) {
            String applied = getStepEventData(saga, "ORDER_CANCELLED");
            if (sameJson(applied, rawEvent)) {
                log.info("[Saga] Exact order-cancelled replay for orderId={}, skipping", orderId);
                return;
            }
            throw new IllegalStateException("Conflicting order-cancelled event for orderId=" + orderId);
        }
        if (saga.getStatus() == SagaStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel completed Saga for orderId=" + orderId);
        }
        if (saga.getStatus() == SagaStatus.FAILED) {
            // A failure compensation can command Order to CANCELLED after Saga has
            // already recorded FAILED. The resulting order event is a consequence,
            // not a new compensation request.
            log.info("Ignoring cancellation consequence for failed Saga orderId={}", orderId);
            return;
        }

        // If a Delivery is known, wait for its durable CANCELLED status event
        // before declaring Saga cancellation complete. When create-delivery is
        // still in flight there is nothing to await; a late result is handled
        // explicitly by handleDeliveryCreated.
        if (saga.getDeliveryId() == null) {
            saga.setStatus(SagaStatus.CANCELLED);
            saga.setCompletedAt(LocalDateTime.now());
        } else {
            saga.setStatus(SagaStatus.COMPENSATING);
            saga.setCompletedAt(null);
        }
        saga.addStep("ORDER_CANCELLED", "order.cancelled", rawEvent);
        sagaInstanceRepository.save(saga);

        try {
            // Enrich payload with deliveryId for Match/Delivery services
            ObjectNode payloadNode = (ObjectNode) objectMapper.readTree(rawEvent);
            if (saga.getDeliveryId() != null) {
                payloadNode.put("deliveryId", saga.getDeliveryId());
            }
            String enrichedEvent = objectMapper.writeValueAsString(payloadNode);

            // ✅ COMPENSATION: Huỷ delivery
            sendCommand(CMD_CANCEL_DELIVERY, orderId.toString(), enrichedEvent);

            // ✅ COMPENSATION: Dừng đúng matching generation hiện hành.
            sendStopMatchingCommand(saga, orderId, enrichedEvent);
        } catch (Exception e) {
            if (e instanceof SagaCommandPublishException publishException) {
                throw publishException;
            }
            // Fallback
            sendCommand(CMD_CANCEL_DELIVERY, orderId.toString(), rawEvent);
            sendStopMatchingCommand(saga, orderId, rawEvent);
        }

        log.warn("🚨 [Saga] COMPENSATION — order cancelled, orderId={}", orderId);
    }

    // ==================== FAILURE HANDLERS ====================

    /**
     * ❌ delivery.created.failed → Tạo delivery thất bại → báo Order cancel
     */
    @Transactional
    public void handleDeliveryCreationFailed(Long orderId, String reason, String rawEvent) {
        if (!claimInbound("delivery.created.failed", orderId, rawEvent)) return;
        SagaInstance saga = findSagaByOrderId(orderId);

        if (saga.getStatus() != SagaStatus.STARTED) {
            log.warn("⚠️ [Saga] handleDeliveryCreationFailed - Saga cho orderId={} đang ở {}, bỏ qua (không phải STARTED)", orderId, saga.getStatus());
            return;
        }

        saga.setStatus(SagaStatus.COMPENSATING);
        saga.addStep("DELIVERY_CREATION_FAILED", "delivery.created.failed", rawEvent);
        sagaInstanceRepository.save(saga);

        log.error("🚨 [Saga] COMPENSATION — Delivery creation failed for orderId={}: {}", orderId, reason);

        // ✅ COMPENSATION: Báo order-service → cập nhật status thất bại
        sendOrderStatusCommand(orderId, "CANCELLED", rawEvent);

        // Đánh dấu saga thất bại
        saga.setStatus(SagaStatus.FAILED);
        saga.setCompletedAt(LocalDateTime.now());
        sagaInstanceRepository.save(saga);
    }

    /**
     * ❌ Xử lý generic failure từ bất kỳ step nào
     */
    @Transactional
    public void handleStepFailed(String stepName, Long orderId, String reason, String rawEvent) {
        if (!claimInbound(stepName + ".failed", orderId, rawEvent)) return;
        SagaInstance saga = findSagaByOrderId(orderId);
        handleStepFailedLocked(stepName, saga, reason, rawEvent);
    }

    /**
     * Applies failure compensation while the Saga aggregate is already locked.
     * Scheduler-originated failures call this directly after their timeout inbox
     * command has been claimed, preventing a second claim with a different topic.
     */
    private void handleStepFailedLocked(String stepName, SagaInstance saga, String reason, String rawEvent) {
        Long orderId = saga.getOrderId();

        // A Delivery refusal after the Order cancellation command is an
        // invariant breach, not an ignorable terminal replay. Record it so the
        // reconciliation/alerting path can recover the Order/Delivery drift.
        if ("DELIVERY_CANCEL".equals(stepName)
                && (saga.getStatus() == SagaStatus.COMPENSATING
                        || saga.getStatus() == SagaStatus.CANCELLED
                        || saga.getStatus() == SagaStatus.FAILED)) {
            saga.addStep("DELIVERY_CANCEL_FAILED", "delivery.cancel.failed", rawEvent);
            saga.setStatus(SagaStatus.FAILED);
            saga.setCompletedAt(LocalDateTime.now());
            sagaInstanceRepository.save(saga);
            log.error("[Saga] Delivery cancellation failed after compensation for orderId={}: {}. "
                    + "Manual reconciliation is required.", orderId, reason);
            return;
        }

        if (saga.getStatus() == SagaStatus.FAILED || saga.getStatus() == SagaStatus.CANCELLED || saga.getStatus() == SagaStatus.COMPLETED) {
            log.warn("⚠️ [Saga] handleStepFailed - Saga cho orderId={} đã ở trạng thái cuối {}, bỏ qua", orderId, saga.getStatus());
            return;
        }

        // ✅ Lưu trạng thái TRƯỚC khi chuyển sang COMPENSATING, để quyết định đúng
        //    hành động bù trừ (bug cũ: switch trên status đã bị ghi đè → luôn vào default).
        SagaStatus prevStatus = saga.getStatus();
        saga.setStatus(SagaStatus.COMPENSATING);
        saga.addStep(stepName + "_FAILED", stepName + ".failed", rawEvent);

        log.error("🚨 [Saga] COMPENSATION — Step {} failed for orderId={} (prevStatus={}): {}",
                stepName, orderId, prevStatus, reason);

        // Compensation dựa trên trạng thái TRƯỚC khi fail
        switch (prevStatus) {
            // DELIVERY_CREATED: đã tạo delivery, đang chờ nhà hàng confirm (chưa match)
            //   → chỉ cần huỷ delivery, không cần stop-matching.
            // FINDING_SHIPPER/SHIPPER_FOUND: đã/đang match → huỷ delivery + dừng match.
            case DELIVERY_CREATED, FINDING_SHIPPER, SHIPPER_FOUND -> {
                String correlatedEvent = rawEvent;
                try {
                    ObjectNode payloadNode = (ObjectNode) objectMapper.readTree(rawEvent);
                    if (saga.getDeliveryId() != null) {
                        payloadNode.put("deliveryId", saga.getDeliveryId());
                    }
                    String enrichedEvent = objectMapper.writeValueAsString(payloadNode);
                    correlatedEvent = enrichedEvent;

                    // A delivery that never entered matching is cancelled. Once
                    // matching started, the canonical terminal state is
                    // SHIPPER_NOT_FOUND instead of CANCELLED.
                    sendCommand(prevStatus == SagaStatus.DELIVERY_CREATED
                                    ? CMD_CANCEL_DELIVERY
                                    : CMD_MARK_SHIPPER_NOT_FOUND,
                            orderId.toString(), enrichedEvent);
                    if (prevStatus != SagaStatus.DELIVERY_CREATED) {
                        sendStopMatchingCommand(saga, orderId, enrichedEvent);
                    }
                } catch (Exception e) {
                    if (e instanceof SagaCommandPublishException publishException) {
                        throw publishException;
                    }
                    sendCommand(prevStatus == SagaStatus.DELIVERY_CREATED
                                    ? CMD_CANCEL_DELIVERY
                                    : CMD_MARK_SHIPPER_NOT_FOUND,
                            orderId.toString(), rawEvent);
                }
                String terminalOrderStatus = prevStatus == SagaStatus.DELIVERY_CREATED
                        ? "CANCELLED"
                        : "SHIPPER_NOT_FOUND";
                sendOrderStatusCommand(orderId, terminalOrderStatus, correlatedEvent);
            }
            default -> {
                // Nếu chưa có gì cần dọn → chỉ báo order failed
                sendOrderStatusCommand(orderId, "CANCELLED", rawEvent);
            }
        }

        saga.setStatus(SagaStatus.FAILED);
        saga.setCompletedAt(LocalDateTime.now());
        sagaInstanceRepository.save(saga);
    }

    /**
     * Replays one valid fact that was durably staged before its Saga existed.
     * The Saga row is locked before the early-event row, matching the creation
     * path's lock order and preventing a lock-order inversion.
     */
    @Transactional
    public void processEarlyEvent(UUID eventId) {
        if (earlyEventRepository == null || eventId == null) {
            return;
        }
        SagaEarlyEvent observed = earlyEventRepository.findById(eventId).orElse(null);
        if (observed == null) {
            return;
        }
        SagaInstance saga = sagaInstanceRepository.findByOrderIdForUpdate(observed.getOrderId()).orElse(null);
        if (saga == null) {
            return;
        }
        SagaEarlyEvent staged = earlyEventRepository.findByIdForUpdate(eventId).orElse(null);
        if (staged == null) {
            return;
        }
        applyEarlyEventLocked(saga, staged);
    }

    // ==================== HELPERS ====================

    private void drainEarlyEventsForSaga(SagaInstance saga) {
        if (earlyEventRepository == null) {
            return;
        }
        for (SagaEarlyEvent staged : earlyEventRepository.findByOrderIdForUpdate(saga.getOrderId())) {
            applyEarlyEventLocked(saga, staged);
        }
    }

    private void applyEarlyEventLocked(SagaInstance saga, SagaEarlyEvent staged) {
        if (!claimInbound(staged.getTopic(), saga.getOrderId(), staged.getPayload())) {
            earlyEventRepository.delete(staged);
            return;
        }
        switch (staged.getTopic()) {
            case "order.cancelled" -> applyOrderCancelledLocked(saga, staged.getPayload());
            case "restaurant.order-confirmed" -> applyRestaurantConfirmedLocked(saga, staged.getPayload());
            default -> throw new IllegalArgumentException("Unsupported staged Saga topic: " + staged.getTopic());
        }
        earlyEventRepository.delete(staged);
        log.info("[Saga] Applied staged {} for orderId={} eventId={}",
                staged.getTopic(), saga.getOrderId(), staged.getEventId());
    }

    private void stageEarlyEvent(String topic, Long orderId, String rawEvent) {
        if (earlyEventRepository == null) {
            throw new IllegalStateException("Early Saga event staging is unavailable for orderId=" + orderId);
        }
        try {
            JsonNode event = objectMapper.readTree(rawEvent);
            JsonNode id = event.get("eventId");
            if (id == null || !id.isTextual()) {
                throw new IllegalArgumentException("Saga early eventId is required");
            }
            UUID eventId = UUID.fromString(id.asText());
            String fingerprint = sha256(rawEvent);
            SagaEarlyEvent existing = earlyEventRepository.findById(eventId).orElse(null);
            if (existing == null) {
                if (insertEarlyEventIfAbsent(eventId, topic, orderId, rawEvent, fingerprint) == 1) {
                    log.info("[Saga] Staged {} before Saga creation for orderId={}, eventId={}",
                            topic, orderId, eventId);
                    return;
                }
                existing = earlyEventRepository.findById(eventId).orElseThrow(() ->
                        new IllegalStateException("Saga early-event conflict resolved without a committed row"));
            }
            requireExactEarlyReplay(existing, topic, orderId, fingerprint);
            log.info("[Saga] Exact early {} replay staged for orderId={}, eventId={}",
                    topic, orderId, eventId);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to stage early Saga event", exception);
        }
    }

    private boolean matchesTimeoutObservation(SagaInstance saga, SagaTimeoutCommand command) {
        if (saga.getStatus() != command.expectedStatus()
                || versionOf(saga) != command.expectedVersion()
                || saga.getUpdatedAt() == null) {
            return false;
        }
        return saga.getUpdatedAt().equals(command.observedAt());
    }

    private long versionOf(SagaInstance saga) {
        return saga.getVersion() == null ? 0L : saga.getVersion();
    }

    /**
     * A malformed persisted offer is treated as due so the timeout command can
     * fail closed through the normal compensation path instead of staying
     * invisible in the scheduler forever.
     */
    private boolean isShipperOfferTimeoutDue(SagaInstance saga) {
        SagaStep foundStep = null;
        for (int i = saga.getSteps().size() - 1; i >= 0; i--) {
            SagaStep step = saga.getSteps().get(i);
            if ("SHIPPER_FOUND".equals(step.getStepName())) {
                foundStep = step;
                break;
            }
        }
        if (foundStep == null || foundStep.getEventData() == null) {
            return true;
        }
        try {
            JsonNode foundPayload = objectMapper.readTree(foundStep.getEventData());
            int offerTimeoutSeconds = foundPayload.hasNonNull("waitingTimeoutSeconds")
                    ? Math.max(1, Math.min(foundPayload.get("waitingTimeoutSeconds").asInt(), 180))
                    : 180;
            LocalDateTime offerFoundAt = foundPayload.hasNonNull("foundAt")
                    ? LocalDateTime.parse(foundPayload.get("foundAt").asText())
                    : foundStep.getExecutedAt();
            if (offerFoundAt == null) {
                return true;
            }
            return !offerFoundAt.plusSeconds(offerTimeoutSeconds).isAfter(LocalDateTime.now());
        } catch (Exception malformed) {
            return true;
        }
    }

    private boolean isCancellationConfirmation(SagaInstance saga) {
        if (saga.getStatus() == SagaStatus.COMPENSATING) {
            return hasStep(saga, "ORDER_CANCELLED");
        }
        // A late create-delivery result can be cancelled after the generic
        // STARTED timeout has already marked the Saga failed. Record that
        // cleanup confirmation rather than sending it to DLT as a contradictory
        // terminal delivery status.
        return saga.getStatus() == SagaStatus.CANCELLED || saga.getStatus() == SagaStatus.FAILED;
    }

    private SagaInstance findSagaByOrderId(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId is required");
        }
        return sagaInstanceRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalStateException("No saga found for orderId=" + orderId));
    }

    private boolean hasRejectedShipper(SagaInstance saga, Long shipperId) {
        for (SagaStep step : saga.getSteps()) {
            if (!step.getStepName().startsWith("SHIPPER_REJECTED") || step.getEventData() == null) {
                continue;
            }
            try {
                JsonNode event = objectMapper.readTree(step.getEventData());
                if (event.hasNonNull("rejectedShipperId")
                        && event.get("rejectedShipperId").asLong() == shipperId) {
                    return true;
                }
            } catch (Exception ignored) {
                // Historic malformed steps remain visible but cannot prove a duplicate.
            }
        }
        return false;
    }

    private boolean sameJson(String left, String right) {
        if (left == null || right == null) return false;
        try {
            return objectMapper.readTree(left).equals(objectMapper.readTree(right));
        } catch (Exception malformed) {
            return false;
        }
    }

    private void requireDeliveryIdentity(SagaInstance saga, Long deliveryId, Long orderId) {
        if (deliveryId == null || deliveryId <= 0) {
            throw new IllegalArgumentException("deliveryId must be positive for orderId=" + orderId);
        }
        if (saga.getDeliveryId() == null || !saga.getDeliveryId().equals(deliveryId)) {
            throw new IllegalStateException("Delivery identity mismatch for orderId=" + orderId
                    + ": expected=" + saga.getDeliveryId() + ", received=" + deliveryId);
        }
    }

    private void requireDeliveryTransition(SagaStatus current, SagaStatus target, Long orderId) {
        boolean valid = switch (target) {
            case PICKING_UP -> current == SagaStatus.SHIPPER_ASSIGNED;
            case DELIVERING -> current == SagaStatus.PICKING_UP;
            case COMPLETED -> current == SagaStatus.DELIVERING;
            case CANCELLED -> Set.of(
                    SagaStatus.STARTED,
                    SagaStatus.DELIVERY_CREATED,
                    SagaStatus.FINDING_SHIPPER,
                    SagaStatus.SHIPPER_FOUND,
                    SagaStatus.SHIPPER_ASSIGNED).contains(current);
            default -> false;
        };
        if (!valid) {
            throw new IllegalStateException("Invalid saga delivery transition " + current + " -> "
                    + target + " for orderId=" + orderId);
        }
    }

    /** Kiểm tra saga đã có một step theo tên chưa. */
    private boolean hasStep(SagaInstance saga, String stepName) {
        return saga.getSteps() != null && saga.getSteps().stream()
                .anyMatch(s -> stepName.equals(s.getStepName()));
    }

    /** Lấy eventData của step gần nhất theo tên (null nếu không có). */
    private String getStepEventData(SagaInstance saga, String stepName) {
        if (saga.getSteps() == null) return null;
        String data = null;
        for (SagaStep s : saga.getSteps()) {
            if (stepName.equals(s.getStepName()) && s.getEventData() != null) {
                data = s.getEventData();
            }
        }
        return data;
    }

    /**
     * Every Find command carries a Saga-owned generation that is independent
     * of the outbox event ID. The persisted MATCHING_STARTED step is the
     * authoritative target for later stop-matching and stale-result fences.
     */
    private String dispatchFindShipperCommand(
            SagaInstance saga,
            Long orderId,
            ObjectNode payload) throws Exception {
        UUID matchingSessionId = nextMatchingSessionId(saga);
        payload.put("matchingSessionId", matchingSessionId.toString());
        String command = objectMapper.writeValueAsString(payload);
        sendCommand(CMD_FIND_SHIPPER, orderId.toString(), command);
        saga.addStep("MATCHING_STARTED", CMD_FIND_SHIPPER, command);
        return command;
    }

    private UUID nextMatchingSessionId(SagaInstance saga) {
        long generation = saga.getSteps() == null ? 1L : saga.getSteps().stream()
                .filter(step -> "MATCHING_STARTED".equals(step.getStepName()))
                .count() + 1L;
        String sagaIdentity = saga.getId() == null
                ? "order:" + saga.getOrderId()
                : saga.getId().toString();
        return UUID.nameUUIDFromBytes(
                ("saga:matching-session:" + sagaIdentity + ":" + generation)
                        .getBytes(StandardCharsets.UTF_8));
    }

    private UUID currentMatchingSessionId(SagaInstance saga) {
        String matchingStart = getStepEventData(saga, "MATCHING_STARTED");
        if (matchingStart == null) {
            return null;
        }
        try {
            JsonNode payload = objectMapper.readTree(matchingStart);
            if (!payload.hasNonNull("matchingSessionId")) {
                // Pre-contract active Sagas cannot safely target a generation.
                // Do not emit a broad stop that could cancel a later rematch.
                return null;
            }
            return UUID.fromString(payload.get("matchingSessionId").asText());
        } catch (Exception malformed) {
            throw new IllegalStateException("Persisted matching session identity is malformed", malformed);
        }
    }

    private boolean isCurrentMatchingResult(SagaInstance saga, String rawEvent) {
        UUID expected = currentMatchingSessionId(saga);
        if (expected == null) {
            // A Saga begun before the generation contract has no safe expected
            // value. Preserve its in-flight compatibility during rollout; new
            // matching attempts always persist the explicit session above.
            return true;
        }
        try {
            JsonNode result = objectMapper.readTree(rawEvent);
            if (!result.hasNonNull("matchingSessionId")) {
                throw new IllegalArgumentException(
                        "Match result matchingSessionId is required for a generation-aware Saga");
            }
            UUID actual = UUID.fromString(result.get("matchingSessionId").asText());
            return expected.equals(actual);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception malformed) {
            throw new IllegalArgumentException("Match result matchingSessionId is malformed", malformed);
        }
    }

    private void sendStopMatchingCommand(SagaInstance saga, Long orderId, String causeEvent) {
        UUID matchingSessionId = currentMatchingSessionId(saga);
        if (matchingSessionId == null) {
            log.info("[Saga] No generation-scoped Match command exists for orderId={}; skipping stop-matching",
                    orderId);
            return;
        }
        if (saga.getDeliveryId() == null || saga.getDeliveryId() <= 0) {
            throw new IllegalStateException(
                    "Cannot stop matching generation without a persisted deliveryId for orderId=" + orderId);
        }
        try {
            ObjectNode stop = objectMapper.createObjectNode();
            stop.put("orderId", orderId);
            stop.put("deliveryId", saga.getDeliveryId());
            stop.put("matchingSessionId", matchingSessionId.toString());
            JsonNode parsedCause = objectMapper.readTree(causeEvent);
            if (parsedCause.hasNonNull("eventId")) {
                stop.put("causeEventId", parsedCause.get("eventId").asText());
            }
            sendCommand(CMD_STOP_MATCHING, orderId.toString(), objectMapper.writeValueAsString(stop));
        } catch (SagaCommandPublishException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot build generation-scoped stop-matching command", exception);
        }
    }

    /**
     * Every matching attempt is rebuilt from Saga-owned canonical state. Rejection
     * and timeout events are control signals, not authorities for price/payment or
     * delivery coordinates.
     */
    private ObjectNode buildFindShipperPayload(SagaInstance saga, String attemptEvent) throws Exception {
        JsonNode parsedAttempt = objectMapper.readTree(attemptEvent);
        if (!(parsedAttempt instanceof ObjectNode)) {
            throw new IllegalArgumentException("Find-shipper payload must be a JSON object");
        }
        ObjectNode payload = objectMapper.createObjectNode();
        copyIfPresent(parsedAttempt, payload,
                "orderId", "deliveryId", "pickupAddress", "pickupLat", "pickupLng",
                "deliveryAddress", "deliveryLat", "deliveryLng", "totalPrice",
                "shippingFee", "paymentMethod", "restaurantId", "restaurantName", "matchingDeadlineAt");
        copyIfPresent(parsedAttempt, payload, "batchOfferEnabled", "batchWave");
        payload.put("batchOfferEnabled", batchClientCapabilityEnabled);

        String deliveryData = getStepEventData(saga, "DELIVERY_CREATED");
        if (deliveryData != null) {
            JsonNode delivery = objectMapper.readTree(deliveryData);
            copyIfPresent(delivery, payload, "deliveryId", "pickupAddress", "pickupLat", "pickupLng",
                    "deliveryAddress", "deliveryLat", "deliveryLng");
        }

        String matchingStartData = getStepEventData(saga, "MATCHING_STARTED");
        if (matchingStartData != null) {
            JsonNode matchingStart = objectMapper.readTree(matchingStartData);
            copyIfPresent(matchingStart, payload, "matchingDeadlineAt");
        }

        JsonNode order = objectMapper.readTree(saga.getPayload());
        copyIfPresent(order, payload, "orderId", "totalPrice", "shippingFee", "paymentMethod",
                "restaurantId", "restaurantName");

        if (!payload.hasNonNull("orderId") || !payload.hasNonNull("deliveryId")) {
            throw new IllegalArgumentException("Canonical matching payload is missing orderId/deliveryId");
        }
        if (!payload.hasNonNull("paymentMethod")
                || !"COD".equalsIgnoreCase(payload.get("paymentMethod").asText())) {
            throw new IllegalArgumentException("COD is the only supported MVP matching payment method");
        }
        if (!payload.hasNonNull("totalPrice") || payload.get("totalPrice").decimalValue().signum() <= 0) {
            throw new IllegalArgumentException("Canonical COD totalPrice must be greater than zero");
        }
        return payload;
    }

    private void copyIfPresent(JsonNode source, ObjectNode target, String... fields) {
        if (source == null || !source.isObject()) return;
        for (String field : fields) {
            if (source.hasNonNull(field)) {
                target.set(field, source.get(field));
            }
        }
    }

    /**
     * Claims a Kafka event before any Saga mutation or command-outbox write. The
     * primary-key claim commits with the Saga mutation/outbox so a concurrent
     * duplicate cannot perform a side effect and a conflicting replay is never
     * silently acknowledged.
     */
    private boolean claimInbound(String topic, Long orderId, String rawEvent) {
        if (inboundReceiptRepository == null) return true;
        try {
            JsonNode event = objectMapper.readTree(rawEvent);
            JsonNode id = event.get("eventId");
            if (id == null || !id.isTextual()) {
                throw new IllegalArgumentException("Saga inbound eventId is required");
            }
            UUID eventId = UUID.fromString(id.asText());
            String fingerprint = sha256(rawEvent);
            var existing = inboundReceiptRepository.findById(eventId).orElse(null);
            if (existing == null) {
                if (insertInboundReceiptIfAbsent(eventId, topic, orderId, fingerprint) == 1) {
                    return true;
                }
                existing = inboundReceiptRepository.findById(eventId).orElseThrow(() ->
                        new IllegalStateException("Saga inbound receipt conflict resolved without a committed row"));
            }
            requireExactInboundReplay(existing, topic, orderId, fingerprint);
            log.info("[Saga] Exact inbound replay eventId={}, topic={}, orderId={}, skipping",
                    eventId, topic, orderId);
            return false;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to claim Saga inbound event", exception);
        }
    }

    private int insertInboundReceiptIfAbsent(UUID eventId, String topic, Long orderId, String fingerprint) {
        if (dataSourceUrl != null && dataSourceUrl.startsWith("jdbc:h2:")) {
            return inboundReceiptRepository.insertIfAbsentH2(eventId, topic, orderId, fingerprint);
        }
        return inboundReceiptRepository.insertIfAbsentPostgres(eventId, topic, orderId, fingerprint);
    }

    private int insertEarlyEventIfAbsent(UUID eventId, String topic, Long orderId,
                                         String payload, String fingerprint) {
        if (dataSourceUrl != null && dataSourceUrl.startsWith("jdbc:h2:")) {
            return earlyEventRepository.insertIfAbsentH2(eventId, topic, orderId, payload, fingerprint);
        }
        return earlyEventRepository.insertIfAbsentPostgres(eventId, topic, orderId, payload, fingerprint);
    }

    private void requireExactInboundReplay(
            com.delivery.saga_orchestrator_service.entity.SagaInboundReceipt existing,
            String topic, Long orderId, String fingerprint) {
        if (!existing.getTopic().equals(topic) || !existing.getOrderId().equals(orderId)
                || !existing.getPayloadFingerprint().equals(fingerprint)) {
            throw new IllegalArgumentException("Saga eventId replay has a contradictory payload");
        }
    }

    private void requireExactEarlyReplay(SagaEarlyEvent existing, String topic, Long orderId, String fingerprint) {
        if (!existing.getTopic().equals(topic) || !existing.getOrderId().equals(orderId)
                || !existing.getPayloadFingerprint().equals(fingerprint)) {
            throw new IllegalArgumentException("Saga early eventId replay has a contradictory payload");
        }
    }

    private String sha256(String payload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest) hex.append(String.format("%02x", value));
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private UUID sendCommand(String topic, String key, String payload) {
        try {
            JsonNode jsonNode = objectMapper.readTree(payload);
            String destination = resolveCommandTopic(topic);
            return outboxService.saveCommand(key, destination, key, jsonNode);
        } catch (Exception e) {
            log.error("💥 [Saga] Failed to store command for {}: {}", topic, e.getMessage(), e);
            throw new SagaCommandPublishException("Failed to store saga command for " + topic, e);
        }
    }

    /**
     * Gửi lệnh update order status (bọc thêm trường sagaStatus)
     */
    private void sendOrderStatusCommand(Long orderId, String sagaStatus, String rawEvent) {
        sendOrderStatusCommand(findSagaByOrderId(orderId), sagaStatus, rawEvent);
    }

    private void sendOrderStatusCommand(SagaInstance saga, String sagaStatus, String rawEvent) {
        try {
            Long orderId = saga.getOrderId();
            ObjectNode command = objectMapper.createObjectNode();
            command.put("orderId", orderId);
            command.put("sagaStatus", sagaStatus);
            command.put("orderStatusSequence", saga.getOrderStatusSequence() + 1);
            command.put("originalEvent", rawEvent);
            command.put("timestamp", System.currentTimeMillis());
            saga.setOrderStatusSequence(saga.getOrderStatusSequence() + 1);
            outboxService.saveCommand(orderId.toString(), updateOrderStatusTopic,
                    orderId.toString(), command);
        } catch (Exception e) {
            log.error("💥 [Saga] Failed to store order status command: {}", e.getMessage(), e);
            throw new SagaCommandPublishException("Failed to store saga order status command", e);
        }
    }

    private boolean isExpectedOfferPersistenceCommand(SagaInstance saga, String commandId) {
        String requested = getStepEventData(saga, "OFFER_PERSIST_REQUESTED");
        if (requested == null) return false;
        try {
            return commandId.equals(objectMapper.readTree(requested).path("cacheCommandEventId").asText());
        } catch (Exception ignored) {
            return false;
        }
    }

    private String resolveCommandTopic(String canonicalTopic) {
        return switch (canonicalTopic) {
            case CMD_CREATE_DELIVERY -> createDeliveryTopic;
            case CMD_CANCEL_DELIVERY -> cancelDeliveryTopic;
            case CMD_FIND_SHIPPER -> findShipperTopic;
            case CMD_CACHE_SHIPPER_FOUND -> cacheShipperFoundTopic;
            case CMD_EXPIRE_SHIPPER_OFFER -> expireShipperOfferTopic;
            case CMD_MARK_SHIPPER_NOT_FOUND -> markShipperNotFoundTopic;
            case CMD_STOP_MATCHING -> stopMatchingTopic;
            case CMD_UPDATE_ORDER_STATUS -> updateOrderStatusTopic;
            default -> throw new IllegalArgumentException("Unsupported Saga command topic: " + canonicalTopic);
        };
    }

    private static final class SagaCommandPublishException extends RuntimeException {
        private SagaCommandPublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
