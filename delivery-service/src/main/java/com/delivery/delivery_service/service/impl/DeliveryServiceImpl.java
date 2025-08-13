package com.delivery.delivery_service.service.impl;

import com.delivery.delivery_service.common.constants.RoleConstants;
import com.delivery.delivery_service.dto.event.FindShipperEvent;
import com.delivery.delivery_service.dto.event.MatchAcceptedEvent;
import com.delivery.delivery_service.dto.event.OrderCreatedEvent;
import com.delivery.delivery_service.common.constants.ShipperActionConstants;
import com.delivery.delivery_service.dto.request.AcceptDeliveryRequest;
import com.delivery.delivery_service.dto.request.AssignDeliveryRequest;
import com.delivery.delivery_service.dto.response.DeliveryResponse;
import com.delivery.delivery_service.dto.response.DeliveryTrackingResponse;
import com.delivery.delivery_service.entity.Delivery;
import com.delivery.delivery_service.entity.DeliveryStatus;
import com.delivery.delivery_service.exception.AccessDeniedException;
import com.delivery.delivery_service.exception.InvalidStatusException;
import com.delivery.delivery_service.exception.ResourceNotFoundException;
import com.delivery.delivery_service.mapper.DeliveryMapper;
import com.delivery.delivery_service.repository.DeliveryRepository;
import com.delivery.delivery_service.service.DeliveryService;
import com.delivery.delivery_service.service.DeliveryEventPublisher;
import com.delivery.delivery_service.service.DeliveryWebSocketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class DeliveryServiceImpl implements DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryMapper deliveryMapper;
    private final DeliveryEventPublisher deliveryEventPublisher;
    private final DeliveryWebSocketService webSocketService;

    // ✅ Constructor Injection Pattern (MANDATORY)
    public DeliveryServiceImpl(DeliveryRepository deliveryRepository,
            DeliveryMapper deliveryMapper,
            DeliveryEventPublisher deliveryEventPublisher,
            DeliveryWebSocketService webSocketService) {
        this.deliveryRepository = deliveryRepository;
        this.deliveryMapper = deliveryMapper;
        this.deliveryEventPublisher = deliveryEventPublisher;
        this.webSocketService = webSocketService;
    }

    @Override
    @Transactional
    public DeliveryResponse createDeliveryFromOrderEvent(OrderCreatedEvent event) {
        try {
            // ✅ Tự động tạo delivery record từ OrderCreatedEvent theo Backend Instructions
            Delivery delivery = new Delivery();

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

            // Set notes
            delivery.setNotes(event.getNotes());

            // Set initial status - PENDING (chờ assign shipper)
            delivery.setStatus(DeliveryStatus.PENDING);

            // Set timestamps
            delivery.setCreatedAt(LocalDateTime.now());
            delivery.setUpdatedAt(LocalDateTime.now());
            delivery.setCreatorId(event.getCreatorId());

            // Ước tính thời gian giao hàng (30 phút mặc định)
            delivery.setEstimatedDeliveryTime(LocalDateTime.now().plusMinutes(30));

            // shipperId sẽ là null cho đến khi được assign
            // delivery.setShipperId(null); // default is null

            // Save delivery
            Delivery savedDelivery = deliveryRepository.save(delivery);

            // ✅ Gửi FindShipperEvent đến Match Service để tìm shipper phù hợp
            publishFindShipperEvent(savedDelivery);

            return deliveryMapper.deliveryToDeliveryResponse(savedDelivery);

        } catch (Exception e) {
            throw new RuntimeException("Failed to create delivery from order event: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public DeliveryResponse assignDelivery(AssignDeliveryRequest request, Long userId, String role) {
        // Chỉ admin mới có thể assign delivery
        // if (!RoleConstants.ADMIN.equals(role)) {
        // throw new AccessDeniedException("Bạn không có quyền phân công giao hàng");
        // }

        // Kiểm tra order đã có delivery chưa
        if (deliveryRepository.existsByOrderId(request.getOrderId())) {
            throw new InvalidStatusException("Đơn hàng đã được phân công giao hàng");
        }

        // Tạo delivery mới
        Delivery delivery = deliveryMapper.assignRequestToDelivery(request);
        // delivery.setCreatorId(userId);

        // Ước tính thời gian giao hàng (30 phút mặc định)
        delivery.setEstimatedDeliveryTime(LocalDateTime.now().plusMinutes(30));

        Delivery savedDelivery = deliveryRepository.save(delivery);
        return deliveryMapper.deliveryToDeliveryResponse(savedDelivery);
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
        if (ShipperActionConstants.ACCEPT.equals(request.getAction()) &&
                request.getEstimatedPickupTime() == null) {
            throw new InvalidStatusException("Estimated pickup time is required when accepting delivery");
        }

        // ✅ Find delivery by order ID
        Delivery delivery = deliveryRepository.findByOrderId(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy thông tin giao hàng cho đơn hàng: " + request.getOrderId()));

        // ✅ Validate delivery status
        if (!DeliveryStatus.PENDING.equals(delivery.getStatus())) {
            throw new InvalidStatusException("Đơn hàng không ở trạng thái chờ nhận (PENDING)");
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
            delivery.setUpdatedAt(LocalDateTime.now());

            // Update shipper location nếu có
            if (request.getCurrentLat() != null && request.getCurrentLng() != null) {
                delivery.setShipperCurrentLat(request.getCurrentLat());
                delivery.setShipperCurrentLng(request.getCurrentLng());
            }

            // Update estimated pickup time
            if (request.getEstimatedPickupTime() != null) {
                delivery.setEstimatedDeliveryTime(
                        LocalDateTime.now().plusMinutes(request.getEstimatedPickupTime().longValue()));
            }

            log.info("✅ Shipper {} ACCEPTED order {}", shipperId, request.getOrderId());

        } else if (ShipperActionConstants.REJECT.equals(request.getAction())) {
            // REJECT logic - không assign shipper, reset lại status
            delivery.setShipperId(null);
            delivery.setStatus(DeliveryStatus.PENDING);
            delivery.setUpdatedAt(LocalDateTime.now());
            delivery.setRejectReason(request.getRejectReason());

            log.info("❌ Shipper {} REJECTED order {} - Reason: {}",
                    shipperId, request.getOrderId(), request.getRejectReason());
        }

        // Update notes nếu có
        if (request.getNotes() != null && !request.getNotes().trim().isEmpty()) {
            String existingNotes = delivery.getNotes() != null ? delivery.getNotes() : "";
            delivery.setNotes(existingNotes + " | Shipper notes: " + request.getNotes());
        }

        Delivery savedDelivery = deliveryRepository.save(delivery);

        // ✅ Publish event based on action
        if (ShipperActionConstants.ACCEPT.equals(request.getAction())) {
            publishMatchAcceptedEvent(savedDelivery, shipperId, request);
            log.info("✅ Delivery {} ACCEPTED successfully by shipper {}", delivery.getId(), shipperId);
        } else if (ShipperActionConstants.REJECT.equals(request.getAction())) {
            publishMatchRejectedEvent(savedDelivery, shipperId, request);
            log.info("❌ Delivery {} REJECTED by shipper {} - Reason: {}",
                    delivery.getId(), shipperId, request.getRejectReason());
        }

        DeliveryResponse response = deliveryMapper.deliveryToDeliveryResponse(savedDelivery);

        // ✅ Send real-time update via WebSocket
        sendDeliveryUpdateViaWebSocket(savedDelivery, response);

        return response;
    }

    @Override
    public DeliveryTrackingResponse getDeliveryTracking(Long deliveryId, Long userId, String role) {
        Delivery delivery = findDeliveryById(deliveryId);

        // Kiểm tra quyền xem tracking
        validateViewPermission(delivery, userId, role);

        DeliveryTrackingResponse trackingResponse = deliveryMapper.deliveryToTrackingResponse(delivery);

        // Tính toán khoảng cách và thời gian ước tính
        if (delivery.getShipperCurrentLat() != null && delivery.getShipperCurrentLng() != null
                && delivery.getDeliveryLat() != null && delivery.getDeliveryLng() != null) {

            double distance = calculateDistance(
                    delivery.getShipperCurrentLat(), delivery.getShipperCurrentLng(),
                    delivery.getDeliveryLat(), delivery.getDeliveryLng());

            trackingResponse.setDistanceToDestination(Math.round(distance * 100.0) / 100.0);
            trackingResponse.setEstimatedMinutes((int) Math.ceil(distance * 5)); // 5 phút/km
        }

        // Set status message
        trackingResponse.setStatusMessage(getStatusMessage(delivery.getStatus()));

        return trackingResponse;
    }

    @Override
    @Transactional
    public DeliveryResponse updateDeliveryStatus(Long deliveryId, DeliveryStatus status, Long userId, String role) {
        Delivery delivery = findDeliveryById(deliveryId);

        // Kiểm tra quyền cập nhật status
        validateStatusUpdatePermission(delivery, status, userId, role);

        // Lưu old status để publish event
        String oldStatus = delivery.getStatus().name();

        updateDeliveryStatusInternal(delivery, status);

        Delivery updatedDelivery = deliveryRepository.save(delivery);

        // ✅ Publish delivery status update event với orderId
        deliveryEventPublisher.publishDeliveryStatusUpdated(
                deliveryId, delivery.getOrderId(), status.name(), oldStatus);

        DeliveryResponse response = deliveryMapper.deliveryToDeliveryResponse(updatedDelivery);

        // ✅ Send real-time update via WebSocket
        sendDeliveryUpdateViaWebSocket(updatedDelivery, response);

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
        // Shipper chỉ xem được delivery của mình, admin xem được tất cả
        if (!RoleConstants.ADMIN.equals(role) && !shipperId.equals(userId)) {
            throw new AccessDeniedException("Bạn không có quyền xem delivery của shipper khác");
        }

        List<Delivery> deliveries = deliveryRepository.findByShipperIdOrderByCreatedAtDesc(shipperId);
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
        // Shipper chỉ xem được delivery của mình, admin xem được tất cả
        if (!RoleConstants.ADMIN.equals(role) && !shipperId.equals(userId)) {
            throw new AccessDeniedException("Bạn không có quyền xem delivery của shipper khác");
        }

        List<Delivery> deliveries = deliveryRepository.findActiveDeliveriesByShipper(shipperId);
        return deliveryMapper.deliveriesToDeliveryResponses(deliveries);
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
        if (RoleConstants.SHIPPER.equals(role) && delivery.getShipperId().equals(userId)) {
            return;
        }

        // User có thể xem delivery nếu là order của họ (cần check thêm orderId với
        // userId)
        // Tạm thời cho phép user xem tất cả, có thể tích hợp với order service sau
        if (RoleConstants.USER.equals(role)) {
            return;
        }

        throw new AccessDeniedException("Bạn không có quyền xem thông tin giao hàng này");
    }

    private void validateStatusUpdatePermission(Delivery delivery, DeliveryStatus newStatus, Long userId, String role) {
        if (RoleConstants.ADMIN.equals(role)) {
            return; // Admin có thể cập nhật tất cả
        }

        // Shipper chỉ có thể cập nhật delivery của mình
        if (RoleConstants.SHIPPER.equals(role) && delivery.getShipperId().equals(userId)) {
            return;
        }

        throw new AccessDeniedException("Bạn không có quyền cập nhật trạng thái giao hàng này");
    }

    private void updateDeliveryStatusInternal(Delivery delivery, DeliveryStatus status) {
        DeliveryStatus currentStatus = delivery.getStatus();

        // Validate status transition
        if (!isValidStatusTransition(currentStatus, status)) {
            throw new InvalidStatusException("Không thể chuyển từ trạng thái " + currentStatus.getDescription()
                    + " sang " + status.getDescription());
        }

        delivery.setStatus(status);

        // Cập nhật timestamp theo status
        switch (status) {
            case PICKED_UP:
                delivery.setPickedUpAt(LocalDateTime.now());
                break;
            case DELIVERED:
                delivery.setDeliveredAt(LocalDateTime.now());
                break;
            case PENDING:
            case ASSIGNED:
            case DELIVERING:
            case CANCELLED:
                // Không cần cập nhật timestamp đặc biệt
                break;
        }
    }

    private boolean isValidStatusTransition(DeliveryStatus currentStatus, DeliveryStatus newStatus) {
        // Định nghĩa các transition hợp lệ
        return switch (currentStatus) {
            case PENDING -> newStatus == DeliveryStatus.ASSIGNED || newStatus == DeliveryStatus.CANCELLED;
            case ASSIGNED -> newStatus == DeliveryStatus.PICKED_UP || newStatus == DeliveryStatus.CANCELLED;
            case PICKED_UP -> newStatus == DeliveryStatus.DELIVERING;
            case DELIVERING -> newStatus == DeliveryStatus.DELIVERED;
            case DELIVERED, CANCELLED -> false; // Không thể thay đổi từ trạng thái cuối
        };
    }

    private String getStatusMessage(DeliveryStatus status) {
        return status.getDescription();
    }

    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        // Sử dụng công thức Haversine để tính khoảng cách
        final double R = 6371; // Bán kính Trái Đất (km)

        double latDistance = Math.toRadians(lat2 - lat1);
        double lngDistance = Math.toRadians(lng2 - lng1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    /**
     * ✅ Gửi FindShipperEvent đến Match Service để tự động tìm shipper phù hợp
     */
    private void publishFindShipperEvent(Delivery delivery) {
        try {
            // Tạo FindShipperEvent từ delivery data
            FindShipperEvent event = new FindShipperEvent(
                    delivery.getId(),
                    delivery.getOrderId(),
                    delivery.getPickupAddress(),
                    delivery.getPickupLat(),
                    delivery.getPickupLng(),
                    delivery.getDeliveryAddress(),
                    delivery.getDeliveryLat(),
                    delivery.getDeliveryLng(),
                    delivery.getEstimatedDeliveryTime(),
                    delivery.getNotes(),
                    delivery.getCreatedAt());

            // Gửi event đến Match Service
            deliveryEventPublisher.publishFindShipperEvent(event);

        } catch (Exception e) {
            // Log lỗi nhưng không throw để không làm fail delivery creation
            // Có thể implement retry mechanism sau
            System.err.println("🔥 Failed to publish FindShipperEvent for delivery: " +
                    delivery.getId() + " - Error: " + e.getMessage());

            throw new RuntimeException("Failed to publish FindShipperEvent: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ Publish MatchAcceptedEvent để thông báo cho Notification Service
     */
    private void publishMatchAcceptedEvent(Delivery delivery, Long shipperId, AcceptDeliveryRequest request) {
        try {
            MatchAcceptedEvent event = MatchAcceptedEvent.builder()
                    .matchId(UUID.randomUUID().toString())
                    .orderId(delivery.getOrderId())
                    .deliveryId(delivery.getId())
                    .shipperId(shipperId)
                    .userId(delivery.getCreatorId()) // Customer ID
                    .pickupAddress(delivery.getPickupAddress())
                    .deliveryAddress(delivery.getDeliveryAddress())
                    .estimatedTime(calculateEstimatedTimeInMinutes(delivery))
                    .timestamp(LocalDateTime.now())
                    .eventType("MATCH_ACCEPTED")
                    .notes(request.getNotes())
                    .build();

            deliveryEventPublisher.publishShipperAcceptedEvent(event);

            log.info("📤 Published ShipperAcceptedEvent for delivery {}, shipper {}",
                    delivery.getId(), shipperId);

        } catch (Exception e) {
            log.error("💥 Failed to publish MatchAcceptedEvent for delivery {}: {}",
                    delivery.getId(), e.getMessage(), e);
            // Don't fail main operation if event publishing fails
        }
    }

    /**
     * ✅ Publish MatchRejectedEvent khi shipper reject đơn
     */
    private void publishMatchRejectedEvent(Delivery delivery, Long shipperId, AcceptDeliveryRequest request) {
        try {
            log.info("📤 Publishing ShipperRejectedEvent for delivery {}, shipper {}",
                    delivery.getId(), shipperId);

            // Create rejection event - reuse MatchAcceptedEvent structure
            MatchAcceptedEvent event = MatchAcceptedEvent.builder()
                    .orderId(delivery.getOrderId())
                    .deliveryId(delivery.getId())
                    .shipperId(shipperId)
                    .shipperName("Shipper " + shipperId) 
                    .shipperPhone("N/A") 
                    .eventType("SHIPPER_REJECTED") // Different event type for rejection
                    .estimatedTime(0) // No pickup time for rejected
                    .pickupAddress(delivery.getPickupAddress())
                    .deliveryAddress(delivery.getDeliveryAddress())
                    .notes(request.getRejectReason()) // Use reject reason as notes
                    .timestamp(LocalDateTime.now())
                    .build();

            // For now, use same event with different status
            deliveryEventPublisher.publishShipperAcceptedEvent(event);

            log.info("📤 Published ShipperRejectedEvent for delivery {}, shipper {} - Reason: {}",
                    delivery.getId(), shipperId, request.getRejectReason());

        } catch (Exception e) {
            log.error("💥 Failed to publish MatchRejectedEvent for delivery {}: {}",
                    delivery.getId(), e.getMessage(), e);
            // Don't fail main operation if event publishing fails
        }
    }

    /**
     * ✅ Send delivery update via WebSocket to relevant parties
     */
    private void sendDeliveryUpdateViaWebSocket(Delivery delivery, DeliveryResponse response) {
        try {
            // Send to customer if delivery has customer info
            if (delivery.getCreatorId() != null) {
                webSocketService.sendDeliveryUpdateToCustomer(delivery.getCreatorId(), response);
            }

            // Send to shipper if assigned
            if (delivery.getShipperId() != null) {
                webSocketService.sendDeliveryUpdateToShipper(delivery.getShipperId(), response);
            }

            // Broadcast to all subscribers (admin, restaurant)
            webSocketService.broadcastDeliveryUpdate(response);

            log.info("📡 Sent WebSocket updates for delivery {} status: {}",
                    delivery.getId(), delivery.getStatus());

        } catch (Exception e) {
            log.error("💥 Failed to send WebSocket update for delivery {}: {}",
                    delivery.getId(), e.getMessage(), e);
            // Don't fail main operation if WebSocket fails
        }
    }

    /**
     * ✅ Calculate estimated time in minutes for event
     */
    private Integer calculateEstimatedTimeInMinutes(Delivery delivery) {
        if (delivery.getEstimatedDeliveryTime() != null) {
            return (int) java.time.Duration.between(LocalDateTime.now(),
                    delivery.getEstimatedDeliveryTime()).toMinutes();
        }
        return 30; // Default 30 minutes
    }

}
