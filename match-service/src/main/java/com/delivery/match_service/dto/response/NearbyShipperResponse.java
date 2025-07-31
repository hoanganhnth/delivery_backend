package com.delivery.match_service.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * ✅ Response DTO cho shipper gần nhất
 * Theo Backend Instructions: Naming convention {Entity}Response
 */
public class NearbyShipperResponse {
    
    private Long shipperId;
    private double latitude;
    private double longitude;
    private double distanceKm;       // Khoảng cách từ điểm giao hàng
    private boolean isOnline;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Ho_Chi_Minh")
    private LocalDateTime lastUpdated;
    
    // Constructors
    public NearbyShipperResponse() {}
    
    public NearbyShipperResponse(Long shipperId, double latitude, double longitude, 
                               double distanceKm, boolean isOnline, LocalDateTime lastUpdated) {
        this.shipperId = shipperId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.distanceKm = distanceKm;
        this.isOnline = isOnline;
        this.lastUpdated = lastUpdated;
    }
    
    // Getters và Setters
    public Long getShipperId() {
        return shipperId;
    }
    
    public void setShipperId(Long shipperId) {
        this.shipperId = shipperId;
    }
    
    public double getLatitude() {
        return latitude;
    }
    
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }
    
    public double getLongitude() {
        return longitude;
    }
    
    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }
    
    public double getDistanceKm() {
        return distanceKm;
    }
    
    public void setDistanceKm(double distanceKm) {
        this.distanceKm = distanceKm;
    }
    
    public boolean isOnline() {
        return isOnline;
    }
    
    public void setOnline(boolean online) {
        isOnline = online;
    }
    
    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }
    
    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
