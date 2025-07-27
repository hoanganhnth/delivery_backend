package com.delivery.shipper_service.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    // Constructors
    public ShipperResponse() {}

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

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public Boolean getIsOnline() {
        return isOnline;
    }

    public void setIsOnline(Boolean isOnline) {
        this.isOnline = isOnline;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public void setRating(BigDecimal rating) {
        this.rating = rating;
    }

    public Integer getCompletedDeliveries() {
        return completedDeliveries;
    }

    public void setCompletedDeliveries(Integer completedDeliveries) {
        this.completedDeliveries = completedDeliveries;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
