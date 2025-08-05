package com.delivery.match_service.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ✅ Match Event DTO để publish đến Notification Service theo Backend Instructions
 * Event này sẽ được gửi khi Match Service tìm thấy shipper phù hợp
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchEvent {
    
    // Match information
    private String matchId;          // UUID của match
    private Long orderId;            // Order ID from delivery service
    private Long deliveryId;         // Delivery ID
    
    // Shipper information
    private Long shipperId;          // ID của shipper được chọn
    private String shipperName;      // Tên shipper
    private String shipperPhone;     // SĐT shipper
    private Double distance;         // Khoảng cách từ shipper đến pickup location (km)
    
    // Customer information  
    private Long userId;             // Customer ID
    private String customerName;     // Tên customer
    private String customerPhone;    // SĐT customer
    
    // Order/Delivery details
    private String restaurantName;   // Tên nhà hàng
    private String pickupAddress;    // Địa chỉ lấy hàng
    private String deliveryAddress;  // Địa chỉ giao hàng
    private BigDecimal orderValue;   // Giá trị đơn hàng
    private BigDecimal estimatedPrice; // Phí giao hàng ước tính
    private Integer estimatedTime;   // Thời gian giao hàng ước tính (phút)
    
    // Event metadata
    private String eventType;        // MATCH_FOUND, MATCH_REQUEST, MATCH_ACCEPTED, MATCH_REJECTED
    private String reason;           // Lý do (cho trường hợp reject)
    private LocalDateTime timestamp; // Thời gian tạo event
    
    // Coordinates for reference
    private Double pickupLat;
    private Double pickupLng;
    private Double deliveryLat;
    private Double deliveryLng;
    
    /**
     * ✅ Factory method tạo MatchFoundEvent
     */
    public static MatchEvent createMatchFoundEvent(Long orderId, Long deliveryId, 
                                                  Long shipperId, String shipperName, String shipperPhone,
                                                  Double distance, String restaurantName,
                                                  String pickupAddress, String deliveryAddress,
                                                  BigDecimal estimatedPrice, Integer estimatedTime) {
        MatchEvent event = new MatchEvent();
        event.setMatchId(java.util.UUID.randomUUID().toString());
        event.setOrderId(orderId);
        event.setDeliveryId(deliveryId);
        event.setShipperId(shipperId);
        event.setShipperName(shipperName);
        event.setShipperPhone(shipperPhone);
        event.setDistance(distance);
        event.setRestaurantName(restaurantName);
        event.setPickupAddress(pickupAddress);
        event.setDeliveryAddress(deliveryAddress);
        event.setEstimatedPrice(estimatedPrice);
        event.setEstimatedTime(estimatedTime);
        event.setEventType("MATCH_FOUND");
        event.setTimestamp(LocalDateTime.now());
        return event;
    }
    
    /**
     * ✅ Factory method tạo MatchRequestEvent
     */
    public static MatchEvent createMatchRequestEvent(Long orderId, Long deliveryId,
                                                    Long shipperId, String shipperName,
                                                    String restaurantName, String customerName,
                                                    String pickupAddress, String deliveryAddress,
                                                    BigDecimal orderValue, BigDecimal estimatedPrice,
                                                    Integer estimatedTime) {
        MatchEvent event = new MatchEvent();
        event.setMatchId(java.util.UUID.randomUUID().toString());
        event.setOrderId(orderId);
        event.setDeliveryId(deliveryId);
        event.setShipperId(shipperId);
        event.setShipperName(shipperName);
        event.setRestaurantName(restaurantName);
        event.setCustomerName(customerName);
        event.setPickupAddress(pickupAddress);
        event.setDeliveryAddress(deliveryAddress);
        event.setOrderValue(orderValue);
        event.setEstimatedPrice(estimatedPrice);
        event.setEstimatedTime(estimatedTime);
        event.setEventType("MATCH_REQUEST");
        event.setTimestamp(LocalDateTime.now());
        return event;
    }
}
