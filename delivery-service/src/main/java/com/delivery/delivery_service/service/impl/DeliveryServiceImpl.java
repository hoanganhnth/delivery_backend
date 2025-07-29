package com.delivery.delivery_service.service.impl;

import com.delivery.delivery_service.common.constants.RoleConstants;
import com.delivery.delivery_service.dto.request.AssignDeliveryRequest;
import com.delivery.delivery_service.dto.request.UpdateLocationRequest;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DeliveryServiceImpl implements DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryMapper deliveryMapper;

    public DeliveryServiceImpl(DeliveryRepository deliveryRepository, DeliveryMapper deliveryMapper) {
        this.deliveryRepository = deliveryRepository;
        this.deliveryMapper = deliveryMapper;
    }

    @Override
    @Transactional
    public DeliveryResponse assignDelivery(AssignDeliveryRequest request, Long userId, String role) {
        // Chỉ admin mới có thể assign delivery
        if (!RoleConstants.ADMIN.equals(role)) {
            throw new AccessDeniedException("Bạn không có quyền phân công giao hàng");
        }

        // Kiểm tra order đã có delivery chưa
        if (deliveryRepository.existsByOrderId(request.getOrderId())) {
            throw new InvalidStatusException("Đơn hàng đã được phân công giao hàng");
        }

        // Tạo delivery mới
        Delivery delivery = deliveryMapper.assignRequestToDelivery(request);
        delivery.setCreatorId(userId);

        // Ước tính thời gian giao hàng (30 phút mặc định)
        delivery.setEstimatedDeliveryTime(LocalDateTime.now().plusMinutes(30));

        Delivery savedDelivery = deliveryRepository.save(delivery);
        return deliveryMapper.deliveryToDeliveryResponse(savedDelivery);
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
                    delivery.getDeliveryLat(), delivery.getDeliveryLng()
            );
            
            trackingResponse.setDistanceToDestination(Math.round(distance * 100.0) / 100.0);
            trackingResponse.setEstimatedMinutes((int) Math.ceil(distance * 5)); // 5 phút/km
        }

        // Set status message
        trackingResponse.setStatusMessage(getStatusMessage(delivery.getStatus()));

        return trackingResponse;
    }

    @Override
    @Transactional
    public DeliveryResponse updateShipperLocation(Long deliveryId, UpdateLocationRequest request, Long userId, String role) {
        Delivery delivery = findDeliveryById(deliveryId);

        // Chỉ shipper được giao hoặc admin mới được update location
        if (!RoleConstants.ADMIN.equals(role) && !delivery.getShipperId().equals(userId)) {
            throw new AccessDeniedException("Bạn không có quyền cập nhật vị trí giao hàng này");
        }

        // Cập nhật vị trí shipper
        delivery.setShipperCurrentLat(request.getLat());
        delivery.setShipperCurrentLng(request.getLng());

        // Nếu có status trong request thì cập nhật luôn
        if (request.getStatus() != null) {
            updateDeliveryStatusInternal(delivery, request.getStatus());
        }

        Delivery updatedDelivery = deliveryRepository.save(delivery);
        return deliveryMapper.deliveryToDeliveryResponse(updatedDelivery);
    }

    @Override
    @Transactional
    public DeliveryResponse updateDeliveryStatus(Long deliveryId, DeliveryStatus status, Long userId, String role) {
        Delivery delivery = findDeliveryById(deliveryId);

        // Kiểm tra quyền cập nhật status
        validateStatusUpdatePermission(delivery, status, userId, role);

        updateDeliveryStatusInternal(delivery, status);

        Delivery updatedDelivery = deliveryRepository.save(delivery);
        return deliveryMapper.deliveryToDeliveryResponse(updatedDelivery);
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
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin giao hàng cho đơn hàng: " + orderId));

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
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin giao hàng với ID: " + deliveryId));
    }

    private void validateViewPermission(Delivery delivery, Long userId, String role) {
        if (RoleConstants.ADMIN.equals(role)) {
            return; // Admin có thể xem tất cả
        }

        // Shipper có thể xem delivery của mình
        if (RoleConstants.SHIPPER.equals(role) && delivery.getShipperId().equals(userId)) {
            return;
        }

        // User có thể xem delivery nếu là order của họ (cần check thêm orderId với userId)
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
            throw new InvalidStatusException("Không thể chuyển từ trạng thái " + currentStatus.getDescription() + " sang " + status.getDescription());
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
            case ASSIGNED -> newStatus == DeliveryStatus.PICKED_UP || newStatus == DeliveryStatus.CANCELLED;
            case PICKED_UP -> newStatus == DeliveryStatus.DELIVERING || newStatus == DeliveryStatus.CANCELLED;
            case DELIVERING -> newStatus == DeliveryStatus.DELIVERED || newStatus == DeliveryStatus.CANCELLED;
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
}
