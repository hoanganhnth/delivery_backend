package com.delivery.delivery_service.service;

import com.delivery.delivery_service.dto.event.OrderCreatedEvent;
import com.delivery.delivery_service.dto.event.OrderCancelledEvent;
import com.delivery.delivery_service.dto.event.ShipperNotFoundEvent;
import com.delivery.delivery_service.dto.request.AcceptDeliveryRequest;
import com.delivery.delivery_service.dto.request.AssignDeliveryRequest;
import com.delivery.delivery_service.dto.response.DeliveryResponse;
import com.delivery.delivery_service.dto.response.DeliveryTrackingResponse;
import com.delivery.delivery_service.entity.DeliveryStatus;

import java.util.List;

public interface DeliveryService {

    /**
     * ✅ Tạo delivery từ OrderCreatedEvent (Kafka event processing)
     */
    DeliveryResponse createDeliveryFromOrderEvent(OrderCreatedEvent event);

    /**
     * ✅ Hủy delivery và ngừng tìm shipper từ OrderCancelledEvent
     */
    void cancelDeliveryFromOrderCancelledEvent(OrderCancelledEvent event);
    
    /**
     * ✅ Cập nhật delivery status khi không tìm được shipper
     */
    void updateDeliveryStatusFromShipperNotFoundEvent(ShipperNotFoundEvent event);

    /**
     * Gán shipper cho đơn hàng
     */
    DeliveryResponse assignDelivery(AssignDeliveryRequest request, Long userId, String role);
    
    /**
     * ✅ Shipper accept delivery assignment
     */
    DeliveryResponse acceptDelivery(AcceptDeliveryRequest request, Long shipperId, String role);

    /**
     * Lấy trạng thái và vị trí giao hàng
     */
    DeliveryTrackingResponse getDeliveryTracking(Long deliveryId, Long userId, String role);

    /**
     * Cập nhật trạng thái giao hàng
     */
    DeliveryResponse updateDeliveryStatus(Long deliveryId, DeliveryStatus status, Long userId, String role);

    /**
     * Lấy thông tin delivery theo ID
     */
    DeliveryResponse getDeliveryById(Long deliveryId, Long userId, String role);

    /**
     * Lấy danh sách delivery của shipper
     */
    List<DeliveryResponse> getDeliveriesByShipper(Long shipperId, Long userId, String role);

    /**
     * Lấy delivery theo order ID
     */
    DeliveryResponse getDeliveryByOrderId(Long orderId, Long userId, String role);

    /**
     * Lấy các delivery đang active của shipper
     */
    List<DeliveryResponse> getActiveDeliveriesByShipper(Long shipperId, Long userId, String role);
    
    /**
     * ✅ ADMIN: Huỷ tất cả delivery chưa hoàn thành (PENDING, FINDING_SHIPPER, ASSIGNED, PICKED_UP, DELIVERING)
     * Dùng để cleanup dữ liệu cũ bị lỗi. Gọi từ Postman.
     * @return Map chứa thống kê: totalFound, cancelled, details
     */
    java.util.Map<String, Object> adminCancelAllNonTerminalDeliveries();
}
