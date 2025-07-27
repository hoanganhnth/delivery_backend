package com.delivery.shipper_service.dto.request;

public class CreateShipperRequest {
    private Long userId;
    private String vehicleType;
    private String licenseNumber;
    private String idCard;
    private String driverImage;

    // Constructors
    public CreateShipperRequest() {}

    public CreateShipperRequest(Long userId, String vehicleType, String licenseNumber, String idCard, String driverImage) {
        this.userId = userId;
        this.vehicleType = vehicleType;
        this.licenseNumber = licenseNumber;
        this.idCard = idCard;
        this.driverImage = driverImage;
    }

    // Getters and Setters
    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public void setLicenseNumber(String licenseNumber) {
        this.licenseNumber = licenseNumber;
    }

    public String getIdCard() {
        return idCard;
    }

    public void setIdCard(String idCard) {
        this.idCard = idCard;
    }

    public String getDriverImage() {
        return driverImage;
    }

    public void setDriverImage(String driverImage) {
        this.driverImage = driverImage;
    }
}
