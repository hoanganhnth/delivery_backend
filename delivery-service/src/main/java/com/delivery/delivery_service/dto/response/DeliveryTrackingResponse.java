package com.delivery.delivery_service.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeliveryTrackingResponse {

    private Long deliveryId;
    private Long orderId;
    private String status;
    private Double shipperCurrentLat;
    private Double shipperCurrentLng;
    private Double deliveryLat;
    private Double deliveryLng;
    private String deliveryAddress;
    private Double distanceToDestination; // km
    private Integer estimatedMinutes; // phút
    private String statusMessage;

    // ✅ Thêm thông tin tài chính/nhà hàng cho tracking
    private java.math.BigDecimal totalPrice; 
    private String paymentMethod;
    private Long restaurantId;

}
