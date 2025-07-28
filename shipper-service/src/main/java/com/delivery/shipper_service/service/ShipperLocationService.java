package com.delivery.shipper_service.service;

import com.delivery.shipper_service.dto.request.UpdateLocationRequest;
import com.delivery.shipper_service.dto.response.ShipperLocationResponse;

import java.util.List;

public interface ShipperLocationService {
    ShipperLocationResponse updateLocationByUserId(Long userId, UpdateLocationRequest request);
    ShipperLocationResponse getLocationByUserId(Long userId);
    List<ShipperLocationResponse> findShippersNearby(Double lat, Double lng, Double radiusKm);
    void deleteLocationByUserId(Long userId);
}
