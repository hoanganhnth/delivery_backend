package com.delivery.shipper_service.service;

import com.delivery.shipper_service.dto.request.CreateShipperRequest;
import com.delivery.shipper_service.dto.request.UpdateShipperRequest;
import com.delivery.shipper_service.dto.response.ShipperResponse;

import java.util.List;

public interface ShipperService {
    // Main shipper operations based on userId (from X-User-Id header)
    ShipperResponse createShipper(CreateShipperRequest request, Long userId, String role);
    ShipperResponse updateShipperByUserId(Long userId, UpdateShipperRequest request);
    void deleteShipperByUserId(Long userId);
    ShipperResponse updateOnlineStatusByUserId(Long userId, Boolean isOnline);
    
    // Read operations
    ShipperResponse getShipperById(Long id);
    ShipperResponse getShipperByUserId(Long userId);
    List<ShipperResponse> getAllShippers();
    List<ShipperResponse> getOnlineShippers();
}
