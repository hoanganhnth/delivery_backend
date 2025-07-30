package com.delivery.shipper_service.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ShipperResponse {
    private Long id;
    private Long userId;
    private String vehicleType;
    private String licenseNumber;
    private String idCard;
    private String driverImage;
    private Boolean isOnline;
    private BigDecimal rating;
    private Integer completedDeliveries;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String phone;

    // Constructors
    public ShipperResponse() {
    }

    public ShipperResponse(Long id, Long userId, String vehicleType, String licenseNumber,
            String idCard, String driverImage, Boolean isOnline,
            BigDecimal rating, Integer completedDeliveries,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.vehicleType = vehicleType;
        this.licenseNumber = licenseNumber;
        this.idCard = idCard;
        this.driverImage = driverImage;
        this.isOnline = isOnline;
        this.rating = rating;
        this.completedDeliveries = completedDeliveries;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
 
}
