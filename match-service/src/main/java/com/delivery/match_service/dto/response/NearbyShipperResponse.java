package com.delivery.match_service.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * ✅ Response DTO cho shipper gần nhất
 * Theo Backend Instructions: Naming convention {Entity}Response
 */
@Getter
@Setter
public class NearbyShipperResponse {

    private double latitude;
    private double longitude;
    private double distanceKm; // Khoảng cách từ điểm giao hàng
    private boolean isOnline;

    private String lastUpdated;

    // Constructors
    public NearbyShipperResponse() {
    }

    public NearbyShipperResponse(double latitude, double longitude,
            double distanceKm, boolean isOnline, String lastUpdated) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.distanceKm = distanceKm;
        this.isOnline = isOnline;
        this.lastUpdated = lastUpdated;
    }

}
