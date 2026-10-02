package com.delivery.shipper_service.dto.request;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateShipperRequest {
    // private Long userId;
    @NotBlank
    @Size(max = 100)
    private String fullName;
    @NotBlank
    @Size(max = 50)
    private String vehicleType;
    @NotBlank
    @Size(max = 50)
    private String licenseNumber;
    @NotBlank
    @Size(max = 20)
    private String idCard;
    private String driverImage;
    @NotBlank
    @Size(max = 15)
    private String phone;
    private String idCardFrontImage;
    private String idCardBackImage;
    private String licenseImage;
    private String licensePlate;

    // Constructors
    public CreateShipperRequest() {}

    public CreateShipperRequest(Long userId, String vehicleType, String licenseNumber, String idCard, String driverImage, String phone) {
        // this.userId = userId;
        this.vehicleType = vehicleType;
        this.licenseNumber = licenseNumber;
        this.idCard = idCard;
        this.driverImage = driverImage;
    }

    // Getters and Setters
    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
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
