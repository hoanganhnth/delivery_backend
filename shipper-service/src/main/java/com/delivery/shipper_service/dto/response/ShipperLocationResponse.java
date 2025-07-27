package com.delivery.shipper_service.dto.response;

import java.time.LocalDateTime;

public class ShipperLocationResponse {
    private Long id;
    private Long shipperId;
    private Double lat;
    private Double lng;
    private LocalDateTime updatedAt;

    // Constructors
    public ShipperLocationResponse() {}

    public ShipperLocationResponse(Long id, Long shipperId, Double lat, Double lng, LocalDateTime updatedAt) {
        this.id = id;
        this.shipperId = shipperId;
        this.lat = lat;
        this.lng = lng;
        this.updatedAt = updatedAt;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getShipperId() {
        return shipperId;
    }

    public void setShipperId(Long shipperId) {
        this.shipperId = shipperId;
    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getLng() {
        return lng;
    }

    public void setLng(Double lng) {
        this.lng = lng;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
