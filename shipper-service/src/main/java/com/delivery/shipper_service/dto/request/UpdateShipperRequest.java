package com.delivery.shipper_service.dto.request;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Null;

public class UpdateShipperRequest {
    @Size(max = 50)
    private String vehicleType;
    @Size(max = 50)
    private String licenseNumber;
    @Size(max = 20)
    private String idCard;
    private String driverImage;
    /**
     * Availability is not profile data. Retain the JSON field solely to return
     * a validation error to legacy callers instead of silently accepting it;
     * callers must use PATCH /api/shippers/online-status.
     */
    @Null(message = "isOnline must be changed through /api/shippers/online-status")
    private Boolean isOnline;
    @Size(max = 15)
    private String phone;
    private String idCardFrontImage;
    private String idCardBackImage;
    private String licenseImage;
    private String licensePlate;

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

    public String getIdCardFrontImage() {
        return idCardFrontImage;
    }

    public void setIdCardFrontImage(String idCardFrontImage) {
        this.idCardFrontImage = idCardFrontImage;
    }

    public String getIdCardBackImage() {
        return idCardBackImage;
    }

    public void setIdCardBackImage(String idCardBackImage) {
        this.idCardBackImage = idCardBackImage;
    }

    public String getLicenseImage() {
        return licenseImage;
    }

    public void setLicenseImage(String licenseImage) {
        this.licenseImage = licenseImage;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public void setLicensePlate(String licensePlate) {
        this.licensePlate = licensePlate;
    }
}
