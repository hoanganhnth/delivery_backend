package com.delivery.shipper_service.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateShipperRequest {
    private Long userId;
    private String vehicleType;
    private String licenseNumber;
    private String idCard;
    private String driverImage;
    private String phone;

    // Constructors
    public CreateShipperRequest() {}

    public CreateShipperRequest(Long userId, String vehicleType, String licenseNumber, String idCard, String driverImage) {
        this.userId = userId;
        this.vehicleType = vehicleType;
        this.licenseNumber = licenseNumber;
        this.idCard = idCard;
        this.driverImage = driverImage;
    }

   
}
