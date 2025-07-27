package com.delivery.shipper_service.service;

import com.delivery.shipper_service.dto.request.CreateShipperRequest;
import com.delivery.shipper_service.dto.request.UpdateShipperRequest;
import com.delivery.shipper_service.dto.response.ShipperResponse;

import java.util.List;

public interface ShipperService {
    ShipperResponse createShipper(CreateShipperRequest request, Long creatorId, String role);
    ShipperResponse updateShipper(Long id, UpdateShipperRequest request, Long creatorId);
    void deleteShipper(Long id, Long creatorId);
    ShipperResponse getShipperById(Long id);
    ShipperResponse getShipperByUserId(Long userId);
    List<ShipperResponse> getAllShippers();
    List<ShipperResponse> getOnlineShippers();
    ShipperResponse updateOnlineStatus(Long id, Boolean isOnline, Long creatorId);
}
