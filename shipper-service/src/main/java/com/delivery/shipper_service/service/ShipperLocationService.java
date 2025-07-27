package com.delivery.shipper_service.service;

import com.delivery.shipper_service.dto.request.UpdateLocationRequest;
import com.delivery.shipper_service.dto.response.ShipperLocationResponse;

import java.util.List;

public interface ShipperLocationService {
    ShipperLocationResponse updateLocation(Long shipperId, UpdateLocationRequest request);
    ShipperLocationResponse getLocationByShipperId(Long shipperId);
    List<ShipperLocationResponse> findShippersNearby(Double lat, Double lng, Double radiusKm);
    void deleteLocation(Long shipperId);
}
