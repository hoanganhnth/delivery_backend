package com.delivery.shipper_service.dto.request;

public class UpdateShipperRequest {
    private String vehicleType;
    private String licenseNumber;
    private String idCard;
    private String driverImage;
    private Boolean isOnline;
    private String phone;

    // Constructors
    public UpdateShipperRequest() {
    }

    public UpdateShipperRequest(String vehicleType, String licenseNumber, String idCard, String driverImage,
            Boolean isOnline,  String phone) {
        this.vehicleType = vehicleType;
        this.licenseNumber = licenseNumber;
        this.idCard = idCard;
        this.driverImage = driverImage;
        this.isOnline = isOnline;
        this.phone = phone;
    }

    // Getters and Setters
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

    public Boolean getIsOnline() {
        return isOnline;
    }

    public void setIsOnline(Boolean isOnline) {
        this.isOnline = isOnline;
    }

  
    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
