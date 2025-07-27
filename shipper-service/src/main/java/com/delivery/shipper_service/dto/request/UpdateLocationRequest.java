package com.delivery.shipper_service.dto.request;

public class UpdateLocationRequest {
    private Double lat;
    private Double lng;

    // Constructors
    public UpdateLocationRequest() {}

    public UpdateLocationRequest(Double lat, Double lng) {
        this.lat = lat;
        this.lng = lng;
    }

    // Getters and Setters
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
}
